package com.abin.mallchat.common.monitor;

import kotlin.jvm.internal.SerializedIr;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jboss.marshalling.SerializabilityChecker;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * @author MECHREVO
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonitorContext implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long userId;

    private Long appId;
}
