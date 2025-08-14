0811
配好环境 

写了拦截器配置类 没有@Configuration导致没有注册到spring容器中，不了解前端，一开始很懵

点击详情页会发me请求 没有返回就到登录页 me中信息在tl中，但是拦截器没有生效就一直没数据

/shop-type/ 打错了，然后被拦截，好好好

加了缓存后加载速度提升很明显
jmter测试
Thread Name:线程组 1-1
Sample Start:2025-08-12 18:47:00 CST
Load time:337
Connect Time:1
Latency:336

Thread Name:线程组 1-1
Sample Start:2025-08-12 18:47:00 CST
Load time:4
Connect Time:0
Latency:4

apifox 注意header

循环版代码锁被提前释放导致多次查询数据库 太菜了 呜呜呜看半天问ai都没搞出来

超卖问题是数据库行锁起作用了

一人一单 锁粒度为每个用户
锁范围要比mysql事务大
this proxy 事务通过代理对象执行，同一个类中非事务方法调用事务方法不生效（this）