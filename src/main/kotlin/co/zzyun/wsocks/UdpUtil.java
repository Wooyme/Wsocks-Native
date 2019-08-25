package co.zzyun.wsocks;

public class UdpUtil {
  public static native long initRaw(String srcIp,int srcPort,String dstIp,int dstPort);
  public static native void sendUdp(long ptr,byte[] buf,int len);
}
