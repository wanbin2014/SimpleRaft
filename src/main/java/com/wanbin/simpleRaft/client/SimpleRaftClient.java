package com.wanbin.simpleRaft.client;

import com.wanbin.simpleRaft.SimpleRaftServer;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.util.CharsetUtil;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class SimpleRaftClient {
    final  static  Logger logger = LoggerFactory.getLogger(SimpleRaftClient.class);
    public static void main(String[] args) {
        CommandLineParser parser = new DefaultParser();
        Options options = new Options();
        options.addOption(Option.builder("i").longOpt("ip").desc("server ip address").hasArg().build());
        options.addOption(Option.builder("p").longOpt("port").desc("server port").type(Number.class).hasArg().build());
        options.addOption(Option.builder("c").longOpt("command").desc("command of state machine").hasArg().build());
        options.addOption(Option.builder("h").longOpt("help").desc("print help").build());

        CommandLine cmd = null;
        EventLoopGroup workGroup = new NioEventLoopGroup();
        try {
            cmd = parser.parse(options, args);

        if (cmd.hasOption("help") || !cmd.hasOption("ip") || !cmd.hasOption("port") || !cmd.hasOption("command")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("SimpleRaftClient <ip> <port> <command>", options);
                return;
            }

        String ip = cmd.getOptionValue("ip");
        int port = ((Number) cmd.getParsedOptionValue("port")).intValue();
        String[] command = cmd.getOptionValue("command").split(" ");

        if (!command[0].equals("ls") && !command[0].equals("add")) {
            System.err.println(command[0] + " is a valid command. Now only supports few command like ls and add");
            return;
        }


        InetSocketAddress addr = new InetSocketAddress(ip,port);



        Bootstrap b = new Bootstrap()
                .group(workGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 100)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addFirst("ReadTimeoutHandler", new ReadTimeoutHandler(30));
                        ch.pipeline().addLast("WriteTimeoutHandler", new WriteTimeoutHandler(30));
                    }
                });

        ChannelFuture f = b.connect(ip, port).addListener((ChannelFuture future) -> {
            if (future.isSuccess()) {
                if (command[0].equals("ls")) {
                    future.channel().write(Unpooled.copyLong(4));
                    future.channel().pipeline().addLast(new LsReplyDecode());
                } else if (command[0].equals("add")) {
                    future.channel().write(Unpooled.copyLong(3));
                    future.channel().write(Unpooled.copyInt(command[1].getBytes(StandardCharsets.UTF_8).length));
                    future.channel().write(Unpooled.copiedBuffer(command[1], CharsetUtil.UTF_8));
                    future.channel().pipeline().addLast(new AddReplyDecode());

                }
                future.channel().flush();
                logger.info("Send a request to server successfully! wait for reply");

            }

        });
        f.channel().closeFuture().sync();
        if (f.channel().pipeline().last() instanceof LsReplyDecode) {
            LsReplyDecode decode = (LsReplyDecode) f.channel().pipeline().last();
            List<String> res = decode.getResponse();
            for(int i = 0; i < res.size(); i++) {
                logger.info(i+1 + "." + res.get(i));
            }
        } else if (f.channel().pipeline().last() instanceof AddReplyDecode) {
            AddReplyDecode decode = (AddReplyDecode) f.channel().pipeline().last();
            if (decode.getResponse() == 0) {
                logger.info("Succeed to execute command!");
            } else if (decode.getResponse() == 2) {
                logger.info("Response timeout!");
            } else {
                logger.info("Occur error, Err code:" + decode.getResponse());
            }
        } else {
            logger.info("internal error!");
        }


        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            workGroup.shutdownGracefully();
        }


    }
}
