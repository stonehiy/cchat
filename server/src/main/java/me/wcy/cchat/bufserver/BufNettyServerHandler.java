package me.wcy.cchat.bufserver;

import com.google.gson.Gson;
import com.stonehiy.protobuf.proto.ChatInfo;

import java.util.Calendar;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import me.wcy.cchat.server.NettyChannelMap;

/**
 * 名称以Inbound开头的类意味着它是一个入站处理程序。名称以Outbound开头的类表示它是出站处理程序。
 */
public class BufNettyServerHandler extends SimpleChannelInboundHandler<ChatInfo.Message> {

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        Channel incoming = ctx.channel();
        System.out.println("[SERVER] - " + incoming.remoteAddress() + " 连接过来\n");

    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        Channel incoming = ctx.channel();
        BufNettyServerBootstrap.channels.remove(incoming);

        System.out.println("[SERVER] - " + incoming.remoteAddress() + " 离开\n");

        // A closed Channel is automatically removed from ChannelGroup,
        // so there is no need to do "channels.remove(ctx.channel());"
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception { // (5)
        Channel incoming = ctx.channel();
        System.out.println("ChatClient:" + incoming.remoteAddress() + "上线");

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Channel incoming = ctx.channel();
        System.out.println("ChatClient:" + incoming.remoteAddress() + "掉线");
        // Channel失效，从Map中移除
        NettyChannelMap.remove(ctx.channel());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ChatInfo.Message msg) {
        //消息会在这个方法接收到，msg就是经过解码器解码后得到的消息，框架自动帮你做好了粘包拆包和解码的工作
        //处理消息逻辑
        //ctx.fireChannelRead(msg);//把消息交给下一个处理器
        System.out.println("ChatInfo.Message msg = " + msg);


        ReferenceCountUtil.release(msg);


    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Channel incoming = ctx.channel();
        // 当出现异常就关闭连接
        System.out.println("ChatClient:" + incoming.remoteAddress()
                + "异常,已被服务器关闭");
        cause.printStackTrace();

        ctx.close();
    }
}