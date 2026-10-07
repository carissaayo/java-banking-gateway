package com.yussufajao.gateway.ratelimit;

public record RateLimitPolicy(
    String id,
    int replenishRate,
    int burstCapacity,
    boolean failClosed
) {
    
    public RateLimitPolicy{
        if (id == null || id.isBlank()){
            throw new IllegalStateException("id must be set");
        }
        if(replenishRate < 1){
            throw new IllegalStateException("replenishRate must be at least 1");
        }
        if(burstCapacity < replenishRate){
            throw new IllegalStateException("burstCapacity must be >= replenishRate")
        }
    }
}
