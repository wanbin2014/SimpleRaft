package com.wanbin.simpleRaft;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;


public class FirstDecode extends ReplayingDecoder<Void> {
    final  static Logger logger = LoggerFactory.getLogger(FirstDecode.class);

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        Integer type = in.readInt();
        if (type.equals(1)) {
            ctx.pipeline().addLast(new RequestVoteDecode());
            ctx.pipeline().remove(this);
        } else if (type.equals(2)) {
            ctx.pipeline().addLast(new RequestAppendEntriesDecode());
            ctx.pipeline().remove(this);
        } else if (type.equals(3)) {
            ctx.pipeline().addLast(new ClientAddRequestDecode(1000));
            ctx.pipeline().remove(this);
        } else if (type.equals(4)) {
            ctx.pipeline().addLast(new ClientLsRequestDecode());
            ctx.pipeline().remove(this);
        }

    }
}



