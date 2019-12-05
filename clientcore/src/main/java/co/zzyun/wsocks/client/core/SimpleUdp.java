package co.zzyun.wsocks.client.core;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class SimpleUdp extends Thread {
  private DatagramSocket socket;
  private boolean running = true;
  private byte[] buf = new byte[1500];
  private Handler<DatagramPacket> handler;
  public SimpleUdp(int port) {
    try {
      socket = new DatagramSocket(port);
      System.out.println("Listen at "+port);
    } catch (SocketException e) {
      e.printStackTrace();
    }
  }

  public void run() {
    if(handler==null)
      throw new IllegalStateException("Handler cannot be null");
    while (running) {
      DatagramPacket packet
        = new DatagramPacket(buf, buf.length);
      try {
        socket.receive(packet);
      } catch (IOException e) {
        e.printStackTrace();
      }
      handler.handle(packet);
    }
    socket.close();
  }

  public SimpleUdp send(int port, InetAddress address, Buffer buffer){
    DatagramPacket packet = new DatagramPacket(buffer.getBytes(),buffer.length(),address,port);
    try {
      this.socket.send(packet);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return this;
  }

  public SimpleUdp handler(Handler<DatagramPacket> handler){
    this.handler = handler;
    return this;
  }

  public void close(){
    this.running = false;
  }
}
