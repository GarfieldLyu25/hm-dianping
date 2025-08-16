package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.service.IFollowService;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private IFollowService followService;
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable Long id, @PathVariable boolean isFollow) {
        return followService.follow(id,isFollow);
    }
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable Long id) {
        UserDTO user = UserHolder.getUser();
        return followService.isFollow(id);
    }
    @GetMapping("/common/{id}")
    public Result common(@PathVariable Long id) {
        return followService.common(id);
    }
}
