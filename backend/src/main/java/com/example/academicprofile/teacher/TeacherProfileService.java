package com.example.academicprofile.teacher;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.academicprofile.common.exception.BusinessException;

@Service
public class TeacherProfileService {

    private final TeacherProfileMapper teacherProfileMapper;

    public TeacherProfileService(TeacherProfileMapper teacherProfileMapper) {
        this.teacherProfileMapper = teacherProfileMapper;
    }

    public void create(TeacherProfile teacherProfile) {
        teacherProfileMapper.insert(teacherProfile);
    }

    public Optional<TeacherProfile> findByUserId(Long userId) {
        return Optional.ofNullable(teacherProfileMapper.selectOne(new LambdaQueryWrapper<TeacherProfile>()
                .eq(TeacherProfile::getUserId, userId)));
    }

    public TeacherProfile getByTeacherId(Long teacherId) {
        TeacherProfile profile = teacherProfileMapper.selectById(teacherId);
        if (profile == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Teacher profile not found");
        }
        return profile;
    }

    @Transactional
    public TeacherProfile updateCurrent(Long teacherId, TeacherProfileRequest request) {
        TeacherProfile profile = getByTeacherId(teacherId);
        validatePublicSlug(teacherId, request.publicSlug());

        profile.setAvatarFileId(request.avatarFileId());
        profile.setDisplayName(request.displayName());
        profile.setTitle(blankToNull(request.title()));
        profile.setDepartment(blankToNull(request.department()));
        profile.setPhone(blankToNull(request.phone()));
        profile.setOffice(blankToNull(request.office()));
        profile.setProfileEmail(blankToNull(request.profileEmail()));
        profile.setBiography(blankToNull(request.biography()));
        profile.setPublicEnabled(Boolean.TRUE.equals(request.publicEnabled()));
        profile.setPublicSlug(blankToNull(request.publicSlug()));
        profile.setFieldVisibilityConfig(blankToNull(request.fieldVisibilityConfig()));
        teacherProfileMapper.updateById(profile);
        return profile;
    }

    @Transactional
    public TeacherProfile updateAvatarFileId(Long teacherId, Long avatarFileId) {
        TeacherProfile profile = getByTeacherId(teacherId);
        profile.setAvatarFileId(avatarFileId);
        teacherProfileMapper.updateById(profile);
        return profile;
    }

    @Transactional
    public void clearAvatarFileId(Long teacherId, Long avatarFileId) {
        TeacherProfile profile = getByTeacherId(teacherId);
        if (avatarFileId != null && avatarFileId.equals(profile.getAvatarFileId())) {
            profile.setAvatarFileId(null);
            teacherProfileMapper.updateById(profile);
        }
    }

    private void validatePublicSlug(Long teacherId, String publicSlug) {
        if (!StringUtils.hasText(publicSlug)) {
            return;
        }
        Long count = teacherProfileMapper.selectCount(new LambdaQueryWrapper<TeacherProfile>()
                .eq(TeacherProfile::getPublicSlug, publicSlug)
                .ne(TeacherProfile::getId, teacherId));
        if (count > 0) {
            throw new BusinessException("Public slug already exists");
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
