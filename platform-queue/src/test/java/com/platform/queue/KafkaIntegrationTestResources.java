package com.platform.queue;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public final class KafkaIntegrationTestResources {

  public static final BlockingQueue<String> RECEIVED_PAYLOADS = new LinkedBlockingQueue<>();

  private KafkaIntegrationTestResources() {}
}
