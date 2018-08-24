package com.stonehiy.protobuf;


import org.junit.Test;

import me.wcy.cchat.server.NettyServerBootstrap;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() throws Exception {
        assertEquals(4, 2 + 2);
    }


    @Test
    public void initServer() throws Exception {
        NettyServerBootstrap serverBootstrap = new NettyServerBootstrap(9999);
        serverBootstrap.bind();

    }


}