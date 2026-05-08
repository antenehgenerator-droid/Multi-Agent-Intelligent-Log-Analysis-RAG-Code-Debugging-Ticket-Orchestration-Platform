package com.platform.queue.it;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/** Test slice: avoids component-scanning production packages (prevents duplicate queue beans). */
@SpringBootApplication
@Import(TestKafkaConsumeHarness.class)
public class MinimalKafkaBootstrapTestApp {}
