package com.kapitonenko.httpserver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by kapiton on 23.02.16.
 */
public class NIOHttpServer {
    private Selector selector;
    private Map<SocketChannel, List<String>> dataMapper;
    private Map<SocketChannel, byte[]> dataToSend;
    private InetSocketAddress listenAddress;
    ExecutorService service = Executors.newCachedThreadPool();

    public static void main(String[] args) throws Exception {
        FileReader.createWatcher();
        new NIOHttpServer(8081).startServer();
    }

    public NIOHttpServer(int port) throws IOException {
        listenAddress = new InetSocketAddress(port);
        dataMapper = new HashMap<>();
        dataToSend = new ConcurrentHashMap<>();
    }

    public void startServer() {
        try {
            this.selector = Selector.open();
            ServerSocketChannel serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);

            serverChannel.socket().bind(listenAddress);
            serverChannel.register(this.selector, SelectionKey.OP_ACCEPT);

        } catch (Throwable e) {
            e.printStackTrace();
        }

        while (true) {
            try {
                this.selector.select(10);
                Iterator<SelectionKey> keys = this.selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();

                    keys.remove();

                    if (!key.isValid()) {
                        System.out.println("invalid key");
                        continue;
                    }

                    if (key.isWritable()) {
                        this.write(key);
                    } else if (key.isReadable()) {
                        this.read(key);
                    } else if (key.isAcceptable()) {
                        this.accept(key);
                    }
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel channel = serverChannel.accept();
        channel.configureBlocking(false);

        dataMapper.put(channel, new ArrayList<>());
        channel.register(this.selector, SelectionKey.OP_READ);
    }

    private void read(SelectionKey key) {
        try {
            SocketChannel channel = (SocketChannel) key.channel();
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            int numRead;
            numRead = channel.read(buffer);

            if (numRead == -1) {
                this.dataMapper.remove(channel);
                channel.close();
                key.cancel();
                return;
            }

            byte[] data = new byte[numRead];
            System.arraycopy(buffer.array(), 0, data, 0, numRead);
            String temp = new String(data);
            dataMapper.get(channel).add(temp);

            if (temp.length() < 4) {
                temp = getMinFourSymbols(channel);
            }
            if(temp.length() > 3) {
                if (temp.substring(temp.length() - 4).equals("\r\n\r\n")) {
                    startProcessingRequest(key, channel);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startProcessingRequest(SelectionKey key, SocketChannel channel) {
        StringBuilder builder = new StringBuilder();
        dataMapper.get(channel).forEach(builder::append);
        String tempStr = builder.toString();
        try {
            service.submit(new SocketProcessorNIO(channel, key, dataToSend, tempStr));
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        dataMapper.remove(channel);
    }

    private String getMinFourSymbols(SocketChannel channel) {
        String temp;
        StringBuilder builder = new StringBuilder();
        List<String> list= dataMapper.get(channel);
        for(int i = list.size() - 1; builder.length() < 4 && i > 0; i--) {
            builder.insert(0, list.get(i));
        }
        temp = builder.toString();
        return temp;
    }

    private void write(SelectionKey key) throws IOException{
        SocketChannel channel = (SocketChannel) key.channel();
        byte[] data = dataToSend.get(channel);
        dataToSend.remove(channel);
        channel.write(ByteBuffer.wrap(data));
        channel.close();
        key.cancel();
    }
}
