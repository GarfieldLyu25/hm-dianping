package com.hmdp;

import com.hmdp.service.IShopService;

import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private IShopService ShopService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Test
    void testSaveShop() throws InterruptedException {
        ShopService.saveShop2Redis(1L,10L);
    }

    private final ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testIdworker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable r = () -> {
            for(int i = 0; i < 100; i++){
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        long start = System.currentTimeMillis();
        for(int i = 0; i < 300; i++){
            es.submit(r);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - start));
    }

}
