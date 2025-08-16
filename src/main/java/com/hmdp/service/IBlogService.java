package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {
    public Result queryBlogById(Long id);

    public Result queryHotBlog(Integer cur);

    public Result likeblog(Long id);

    public Result bloglikes(Long id);

    Result queryBlogOfFollow(Long max, Integer offset);
}
