package cn.gov.enterprise.modules.system.profile.controller;

import cn.gov.enterprise.common.api.ApiResponse;
import cn.gov.enterprise.modules.system.profile.service.ProfileService;
import cn.gov.enterprise.modules.system.profile.vo.ProfileVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/profile")
public class ProfileController {
    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('profile:view')")
    public ApiResponse<ProfileVO> currentProfile() {
        return ApiResponse.success(service.currentProfile());
    }
}
