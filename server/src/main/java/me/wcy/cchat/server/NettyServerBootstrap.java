package me.wcy.cchat.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;

public class NettyServerBootstrap {
    private int port;

    public NettyServerBootstrap(int port) {
        this.port = port;
    }

    public void bind() {
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
                        @Override
                        protected void initChannel(SocketChannel socketChannel) {
                            ChannelPipeline pipeline = socketChannel.pipeline();
                            //字符串解码和编码
                            pipeline.addLast("decoder", new ObjectEncoder());
                            pipeline.addLast("encoder", new ObjectDecoder(ClassResolvers.cacheDisabled(null)));
                            //服务器的逻辑
                            pipeline.addLast("handler", new NettyServerHandler());
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
