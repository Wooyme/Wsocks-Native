package co.zzyun.wsocks;

public class PcapJNI {
    static {
      System.load("libPcapJNI.so");
    }
    public static native long initPcap(String srcIp,String srcMacAddress,String gatewayMacAddress);
    public static native void sendUdp(long ptr,String srcIpAddress,String dstIpAddress,int srcPort,int dstPort,byte[] rawData,int len);
}
