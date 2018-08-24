package me.wcy.cchat.bufserver;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * 心跳检测
 *
 * @author kokjuis 189155278@qq.com
 * @ClassName BufHeartBeatServerHandler
 * @Description TODO
 * @date 2016-9-26
 * @content
 */
public class BufHeartBeatServerHandler extends ChannelInboundHandlerAdapter {

    private int loss_connect_time = 0;

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt)
            throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                loss_connect_time++;
                System.out.println("[60 秒没有接收到客户端" + ctx.channel().id()
                        + "的信息了]");
                if (loss_connect_time > 2) {
                    // 超过20秒没有心跳就关闭这个连接
                    System.out.println("[关闭这个不活跃的channel:" + ctx.channel().id()
                            + "]");
                    ctx.channel().close();
                }
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

}
