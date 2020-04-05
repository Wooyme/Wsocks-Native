class Data{
  public String ip;
  public int lag;
  public Data(String ip,int lag){
    this.ip = ip;
    this.lag = lag;
  }

  @Override
  public String toString() {
    return ip;
  }
}
