package com.aiyi.game.dnfserver.service.extension;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Bounded, one-use challenges kept separate from launcher registration challenges. */
public final class PasswordChallenges {
    private static final class Challenge {
        final String value;final long expires;
        Challenge(String value,long expires){this.value=value;this.expires=expires;}
    }
    private final ConcurrentHashMap<String,Challenge> values=new ConcurrentHashMap<>();
    private final LongSupplier clock;
    public PasswordChallenges(){this(System::currentTimeMillis);}
    PasswordChallenges(LongSupplier clock){this.clock=clock;}
    public synchronized void issue(String id,String value){
        long now=clock.getAsLong();values.entrySet().removeIf(e->e.getValue().expires<=now);
        if(values.size()>=1000&&!values.containsKey(id))throw new ActivityConfigService.Failure(429,"验证码请求过多，请稍后重试");
        values.put(id,new Challenge(value,now+300000));
    }
    public boolean consume(String id,String value){
        Challenge c=values.remove(id);
        return c!=null&&c.expires>clock.getAsLong()&&c.value.equalsIgnoreCase(value);
    }
}
