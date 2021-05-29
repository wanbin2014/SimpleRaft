package org.simpleRaft.simpleRaft;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class SimpleRaftServer {
    final static Logger logger = LoggerFactory.getLogger(SimpleRaftServer.class);

    static EventLoopGroup workerGroup = new NioEventLoopGroup();

    public static void start(String ip, int port, String[] members) throws Exception {

        EventLoopGroup bossGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast("ReadTimeoutHandler", new ReadTimeoutHandler(3000));
                            ch.pipeline().addLast("WriteTimeoutHandler", new WriteTimeoutHandler(3000));
                            ch.pipeline().addLast(new FirstDecode());
                        }
                    });
            ChannelFuture f = b.bind(ip, port).sync();

            State.setCandidateId(ip + ":" + port);
            State.setMembers(members);
            State.load();
            State.startElection(new Random().nextInt(10));

            f.channel().closeFuture().sync();
            System.out.println("server quit");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
            State.shutdown();
        }

    }
    public static void main(String[] args) {
        CommandLineParser parser = new DefaultParser();
        Options options = new Options();
        options.addOption(Option.builder("i").longOpt("ip").desc("listen address").hasArg().build());
        options.addOption(Option.builder("p").longOpt("port").desc("listen port").type(Number.class).hasArg().build());
        options.addOption(Option.builder("m").longOpt("members").desc("members").hasArg().build());
        options.addOption(Option.builder("h").longOpt("help").desc("print help").build());

        try {
            CommandLine cmd = parser.parse(options, args);
            if (cmd.hasOption("help") || !cmd.hasOption("ip") || !cmd.hasOption("port") || !cmd.hasOption("members")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("SimpleRaft <ip> <port> <members> " +
                        "The pattern of members is ip:port,ip:port,...", options);
                return;
            }

            String ip = cmd.getOptionValue("ip");
            int port = ((Number) cmd.getParsedOptionValue("port")).intValue();
            String members = cmd.getOptionValue("members");
            String[] member = members.split(",");
            String[] memberExcludeSelf = new String[member.length-1];
            int i , j = 0;
            for (i = 0; i < member.length; i++) {
                String[] fields = member[i].split(":");
                if (fields[0].equals(ip) && Integer.valueOf(fields[1]).equals(port)) {
                    continue;
                }
                memberExcludeSelf[j] = member[i];
                j++;
            }
            if (memberExcludeSelf.length != member.length-1) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("The members of cluster should contains this server itself.", options);
            }

            SimpleRaftServer.start(ip, port, memberExcludeSelf);


        } catch (Exception e) {
            e.printStackTrace();
        }


    }
}
