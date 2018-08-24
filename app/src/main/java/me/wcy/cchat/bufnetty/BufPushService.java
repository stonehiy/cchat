package me.wcy.cchat.bufnetty;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import com.stonehiy.protobuf.proto.ChatInfo;

import java.net.InetSocketAddress;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.ReferenceCountUtil;
import me.wcy.cchat.AppCache;
import me.wcy.cchat.model.CMessage;
import me.wcy.cchat.model.Callback;
import me.wcy.cchat.model.LoginInfo;
import me.wcy.cchat.model.LoginStatus;
import me.wcy.cchat.model.MsgType;

/**
 * Created by hzwangchenyan on 2017/12/26.
 */
public class BufPushService extends Service {
    private static final String TAG = "PushService";
    private static final String HOST = "192.168.0.123";
    private static final int PORT = 9999;

    private SocketChannel socketChannel;
    private Callback<ChatInfo.Message> loginCallback;
    private Callback<ChatInfo.Message> receiveMsgCallback;
    private Handler handler;
    private LoginStatus status = LoginStatus.UNLOGIN;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler();
        AppCache.setBufService(this);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void setReceiveMsgCallback(Callback<ChatInfo.Message> receiveMsgCallback) {
        this.receiveMsgCallback = receiveMsgCallback;
    }

    private void connect(@NonNull Callback<Void> callback) {
        if (status == LoginStatus.CONNECTING) {
            return;
        }

        updateStatus(LoginStatus.CONNECTING);
        NioEventLoopGroup group = new NioEventLoopGroup();
        new Bootstrap()
                .channel(NioSocketChannel.class)
                .group(group)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        ChannelPipeline pipeline = socketChannel.pipeline();
                        pipeline.addLast(new IdleStateHandler(0, 30, 0));
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
                        pipeline.addLast("handler", new ChannelHandle());
                    }
                })
                .connect(new InetSocketAddress(HOST, PORT))
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        socketChannel = (SocketChannel) future.channel();
                        callback.onEvent(200, "success", null);
                    } else {
                        Log.e(TAG, "connect failed");
                        close();
                        // 这里一定要关闭，不然一直重试会引发OOM
                        future.channel().close();
                        group.shutdownGracefully();
                        callback.onEvent(400, "connect failed", null);
                    }
                });
    }

    public void login(String account, String token, Callback<ChatInfo.Message> callback) {
        if (status == LoginStatus.CONNECTING || status == LoginStatus.LOGINING) {
            return;
        }
        connect((code, msg, aVoid) -> {
            if (code == 200) {
                ChatInfo.LoginRequest build = ChatInfo.LoginRequest.newBuilder()
                        .setUsername(ByteString.copyFromUtf8(account))
                        .setPassword(token)
                        .build();

                ChatInfo.Message message = ChatInfo.Message.newBuilder()
                        .setRequest(
                                ChatInfo.Request.newBuilder()
                                        .setLogin(build).build())
                        .build();

                socketChannel.writeAndFlush(message)
                        .addListener((ChannelFutureListener) future -> {
                            if (future.isSuccess()) {
                                loginCallback = callback;
                            } else {
                                close();
                                updateStatus(LoginStatus.UNLOGIN);
                                if (callback != null) {
                                    handler.post(() -> callback.onEvent(400, "failed", null));
                                }
                            }
                        });
            } else {
                close();
                updateStatus(LoginStatus.UNLOGIN);
                if (callback != null) {
                    handler.post(() -> callback.onEvent(400, "failed", null));
                }
            }
        });
    }

    public void sendMsg(CMessage message, Callback<Void> callback) {
        if (status != LoginStatus.LOGINED) {
            callback.onEvent(401, "unlogin", null);
            return;
        }

        socketChannel.writeAndFlush(message.toJson())
                .addListener((ChannelFutureListener) future -> {
                    if (callback == null) {
                        return;
                    }
                    if (future.isSuccess()) {
                        handler.post(() -> callback.onEvent(200, "success", null));
                    } else {
                        handler.post(() -> callback.onEvent(400, "failed", null));
                    }
                });
    }

    private void close() {
        if (socketChannel != null) {
            socketChannel.close();
            socketChannel = null;
        }
    }

    private class ChannelHandle extends SimpleChannelInboundHandler<ChatInfo.Message> {

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            super.channelInactive(ctx);
            BufPushService.this.close();
            updateStatus(LoginStatus.UNLOGIN);
            retryLogin(3000);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            super.userEventTriggered(ctx, evt);
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent e = (IdleStateEvent) evt;
                if (e.state() == IdleState.WRITER_IDLE) {
                    // 空闲了，发个心跳吧
                    CMessage message = new CMessage();
                    message.setFrom(AppCache.getMyInfo().getAccount());
                    message.setType(MsgType.PING);
                    ctx.writeAndFlush(message.toJson());
                }
            }
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ChatInfo.Message msg) throws Exception {
            switch (msg.getMsgType().getNumber()) {
                case ChatInfo.MSG.Login_Response_VALUE:
                    if (msg.getResponse().getResult()) {
                        updateStatus(LoginStatus.LOGINED);
                        if (loginCallback != null) {
                            handler.post(() -> {
                                loginCallback.onEvent(200, "success", msg);
                                loginCallback = null;
                            });
                        }
                    } else {
                        close();
                        updateStatus(LoginStatus.UNLOGIN);
                        if (loginCallback != null) {
                            handler.post(() -> {
                                loginCallback.onEvent(400, msg.getResponse().getErrorDescribe().toString(), msg);
                                loginCallback = null;
                            });
                        }
                    }

                    break;

                case ChatInfo.MSG.Keepalive_Response_VALUE:
                    Log.d(TAG, "receive ping from server");
                    break;

                case ChatInfo.MSG.Send_Message_Response_VALUE:
                    Log.d(TAG, "receive text message " + msg.getNotification().getMsgOrBuilder().getText());
                    if (receiveMsgCallback != null) {
                        handler.post(() -> receiveMsgCallback.onEvent(200, "success", msg));
                    }
                    break;

            }

            ReferenceCountUtil.release(msg);
        }
    }

    private void retryLogin(long mills) {
        if (AppCache.getMyInfo() == null) {
            return;
        }
        handler.postDelayed(() -> login(AppCache.getMyInfo().getAccount(), AppCache.getMyInfo().getToken(), (code, msg, aVoid) -> {
            if (code != 200) {
                retryLogin(mills);
            }
        }), mills);
    }

    private void updateStatus(LoginStatus status) {
        if (this.status != status) {
            Log.d(TAG, "update status from " + this.status + " to " + status);
            this.status = status;
        }
    }
}
