package me.wcy.cchat.bufserver;

import com.stonehiy.protobuf.proto.ChatInfo;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.GlobalEventExecutor;
import me.wcy.cchat.server.NettyServerHandler;

public class BufNettyServerBootstrap {

    // 实例一个连接容器保存连接
    public static ChannelGroup channels = new DefaultChannelGroup(
            GlobalEventExecutor.INSTANCE);

    // 再搞个map保存与用户的映射关系
    public static ConcurrentMap<Integer, ChannelId> userSocketMap = new ConcurrentHashMap<Integer, ChannelId>();

    private int port;

    public BufNettyServerBootstrap(int port) {
        this.port = port;
    }

    public void bind() throws Exception {
        //配置服务端的NIO线程组
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .option(ChannelOption.TCP_NODELAY, true) // 不延迟，直接发送
                    .childOption(ChannelOption.SO_KEEPALIVE, true) // 保持长连接状态
                    .childHandler(new ChannelInitializer<SocketChannel>() {

                        /**
                         ChannelPipeline p = ...;
                         p.addLast（“1”，new InboundHandlerA（））;
                         p.addLast（“2”，new InboundHandlerB（））;
                         p.addLast（“3”，new OutboundHandlerA（））;
                         p.addLast（“4”，new OutboundHandlerB（））;
                         p.addLast（“5”，new InboundOutboundHandlerX（））;

                         在上面的例子中，名称以Inbound开头的类意味着它是一个入站处理程序。名称以Outbound开头的类表示它是出站处理程序。
                         在给定的示例配置中，当事件进入时，处理程序评估顺序为1，2，3，4，5。当事件出站时，顺序为5,4,3,2,1。在此原则之上，ChannelPipeline跳过某些处理程序的评估以缩短堆栈深度：
                         3和4不实现ChannelInboundHandler，因此入站事件的实际评估顺序将为：1,2和5。
                         1和2不实现ChannelOutboundHandler，因此出站事件的实际评估顺序将为：5，4和3。
                         如果5实现ChannelInboundHandler和ChannelOutboundHandler，则入站和出站事件的评估顺序可分别为125和543。
                         */
                        @Override
                        protected void initChannel(SocketChannel socketChannel) {

                            ChannelPipeline pipeline = socketChannel.pipeline();
                            // ----Protobuf处理器，这里的配置是关键----
                            pipeline.addLast("frameDecoder", new ProtobufVarint32FrameDecoder());// 用于decode前解决半包和粘包问题（利用包头中的包含数组长度来识别半包粘包）
                            //配置Protobuf解码处理器，消息接收到了就会自动解码，ProtobufDecoder是netty自带的，Message是自己定义的Protobuf类
                            pipeline.addLast("protobufDecoder",
                                    new ProtobufDecoder(ChatInfo.Message.getDefaultInstance()));
                            // 用于在序列化的字节数组前加上一个简单的包头，只包含序列化的字节长度。
                            pipeline.addLast("frameEncoder",
                                    new ProtobufVarint32LengthFieldPrepender());
                            //配置Protobuf编码器，发送的消息会先经过编码
                            pipeline.addLast("protobufEncoder", new ProtobufEncoder());
                            // ----Protobuf处理器END----

                            //pipeline.addLast("handler", new ChatServerHandler());//自己定义的消息处理器，接收消息会在这个类处理
                            //pipeline.addLast("ackHandler", new AckServerHandler());//处理ACK
                            pipeline.addLast("timeout", new IdleStateHandler(100, 0, 0,
                                    TimeUnit.SECONDS));// //此两项为添加心跳机制,60秒查看一次在线的客户端channel是否空闲
                            pipeline.addLast(new BufHeartBeatServerHandler());// 心跳处理handler
                            //服务器的逻辑
                            pipeline.addLast("handler", new BufNettyServerHandler());
                        }
                    });
//                    .bind(port)
//                    .addListener((ChannelFutureListener) future -> {
//                        if (future.isSuccess()) {
//                            System.out.println("netty server start");
//                        } else {
//                            System.out.println("netty server start failed");
//                        }
//                    });


            //绑定端口，同步等待成功
            ChannelFuture sync = serverBootstrap.bind(port).sync();
            //等待服务器监听端口关闭
            sync.channel().closeFuture().sync();

        } catch (InterruptedException e) {
            e.printStackTrace();

        } finally {
            //优雅退出，释放线程池资源
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }


    }
}
