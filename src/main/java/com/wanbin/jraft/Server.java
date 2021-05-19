package com.wanbin.jraft;

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

import java.util.*;

public class Server {


    static EventLoopGroup workerGroup = new NioEventLoopGroup();

    public static void start(String ip, int port, String[] members) throws Exception {
        State.load();
        State.setCandidateId(ip + ":" + port);
        State.setMembers(members);
        State.startElection(new Random().nextInt() % 10);

        EventLoopGroup bossGroup = new NioEventLoopGroup();


        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast("ReadTimeoutHandler", new ReadTimeoutHandler(30));
                            ch.pipeline().addLast("WriteTimeoutHandler", new WriteTimeoutHandler(30));
                            ch.pipeline().addLast(new FirstDecode());
                        }
                    });
            ChannelFuture f = b.bind(ip, port).sync();
            f.channel().closeFuture().sync();
            System.out.println("server quit");
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
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
                formatter.printHelp("jraft <ip> <port> <members>" +
                        "split multiple member by comma. The pattern of member is ip:port", options);
                return;
            }

            String ip = cmd.getOptionValue("ip");
            int port = ((Number) cmd.getParsedOptionValue("port")).intValue();
            String members = cmd.getOptionValue("members");
            String[] member = members.split(",");
            int i = 0;
            for (i = 0; i < member.length; i++) {
                String[] fields = member[i].split(":");
                if (fields[0].equals(ip) && Integer.getInteger(fields[1]).equals(port)) {
                    break;
                }
            }
            if (i == member.length) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("The members of cluster should require local server.", options);
            }

            Server.start(ip, port, member);


        } catch (Exception e) {
            e.printStackTrace();
        }


    }
}
