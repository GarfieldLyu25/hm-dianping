package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.service.IShopService;

import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;



@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private IShopService ShopService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Test
    void testSaveShop() throws InterruptedException {
        ShopService.saveShop2Redis(1L,10L);
    }
    @Resource
    private IUserService userService;
    private final ExecutorService es = Executors.newFixedThreadPool(500);
    long number = 13373960000L;
    @Test
    void saveUser() throws IOException {
        try (FileWriter writer = new FileWriter("tokens.txt",true)) {
            for (int i = 0; i < 100; i++) {
                String phone = String.valueOf(number);
                number++;
                User user = userService.createUserWithPhone(phone);
                // 7.保存用户信息到 redis中
                // 7.1.随机生成token，作为登录令牌
                String token = UUID.randomUUID().toString(true);
                // 7.2.将User对象转为HashMap存储
                UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
                Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                        CopyOptions.create()
                                .setIgnoreNullValue(true)
                                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
                // 7.3.存储
                String tokenKey = LOGIN_USER_KEY + token;
                stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
                // 7.4.设置token有效期
                stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
                writer.write(token+"\n");
                writer.flush(); // 确保及时写入磁盘
            }
        }
    }


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


    @Resource
    private RedissonClient redissonClient;
//    @Resource
//    private RedissonClient redissonClient2;
//    @Resource
//    private RedissonClient redissonClient3;

    @Test
    void testRedisson() throws Exception{
        //获取锁(可重入)，指定锁的名称
        RLock lock = redissonClient.getLock("anyLock");
        //尝试获取锁，参数分别是：获取锁的最大等待时间(期间会重试)，锁自动释放时间，时间单位
        boolean isLock = lock.tryLock(1,10, TimeUnit.SECONDS);
        //判断获取锁成功
        if(isLock){
            try{
                System.out.println("执行业务");
            }finally{
                //释放锁
                lock.unlock();
            }

        }
    }
    @BeforeEach
    void setUp() {
        RLock lock1 = redissonClient.getLock("anyLock");
//        RLock lock2 = redissonClient2.getLock("anyLock");
//        RLock lock3 = redissonClient3.getLock("anyLock");
        //redissonClient.getMultiLock(lock1,lock2,lock3);
    }
}

