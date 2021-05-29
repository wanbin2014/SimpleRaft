package org.simpleRaft.simpleRaft;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class ClientLsRequestDecode extends ReplayingDecoder<Void> {
    final static Logger logger = LoggerFactory.getLogger(ClientLsRequestDecode.class);
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {

        logger.info("Received ls command from client");
        if (!State.leaderId.equals(State.candidateId)) {
            logger.info("Cannot accept command, but redirect to leader.");
            Bootstrap b = new Bootstrap()
                    .group(SimpleRaftServer.workerGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 100)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new SocksCopyHandler(ctx.channel()));
                        }
                    });
            String[] fields = State.leaderId.split(":");
            b.connect(fields[0], Integer.valueOf(fields[1])).addListener((ChannelFuture future) -> {
                if (future.isSuccess()) {
                    future.channel().write(Unpooled.copyInt(4));
                    future.channel().flush();
                }
            });
            return;
        }

        int j = 0;
        ctx.channel().write(Unpooled.copyLong(State.commitIndex > 100 ? 100 : State.commitIndex));
        for(long i = State.commitIndex ; i >= 0; i-- ) {
            if (j < 100) {
                int size = State.log.get((int)i).toString().getBytes(StandardCharsets.UTF_8).length;
                ctx.channel().write(Unpooled.copyInt(size));
                ctx.channel().write(Unpooled.copiedBuffer(State.log.get((int)i).toString(), CharsetUtil.UTF_8));
                j++;
            }
        }
        ctx.channel().flush();
    }
}
