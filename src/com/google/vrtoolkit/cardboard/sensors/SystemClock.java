package com.google.vrtoolkit.cardboard.sensors;

public class SystemClock
  implements Clock
{
  public long nanoTime()
  {
    return System.nanoTime();
  }
}