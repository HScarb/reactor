package com.cnscarb.reactor;

import com.cnscarb.reactor.bio.BioServer;
import com.cnscarb.reactor.reactor.MultiReactorBootstrap;
import com.cnscarb.reactor.reactor.NioHandler;
import com.cnscarb.reactor.reactor.MultiThreadNioHandler;
import com.cnscarb.reactor.reactor.Reactor;
import com.cnscarb.reactor.reactor.ReactorGroup;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    public static final int PORT = 8080;

    public static void main(String[] args) throws IOException {
        // runBioServer();
        // runSingleThreadReactor();
        // runMultiThreadReactor();
        runMultiReactor();
        System.out.println("Server started on port " + PORT);
    }

    public static void runBioServer() {
        final BioServer bioServer = new BioServer(PORT);

        ExecutorService mainThread = Executors.newSingleThreadExecutor();
        mainThread.submit(bioServer);
        mainThread.shutdown();
    }

    public static void runSingleThreadReactor() throws IOException {
        final Reactor reactor = new Reactor(PORT, NioHandler.class);

        reactor.startThread();
        reactor.executor.shutdown();
    }

    public static void runMultiThreadReactor() throws IOException {
        final Reactor reactor = new Reactor(PORT, MultiThreadNioHandler.class);

        reactor.startThread();
        reactor.executor.shutdown();
    }

    public static void runMultiReactor() throws IOException {
        ReactorGroup mainReactorGroup = new ReactorGroup(1);
        ReactorGroup subReactorGroup = new ReactorGroup(4);
        new MultiReactorBootstrap(PORT, mainReactorGroup, subReactorGroup, NioHandler.class);
    }
}
