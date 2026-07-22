// Provider 消息编解码契约
package com.jswarm.spi.message;

import java.util.List;

public interface MessageCodec<P> {

    List<CanonicalMessage> decode(List<P> providerMessages);

    List<P> encode(List<CanonicalMessage> messages);
}
