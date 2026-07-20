package com.middleware.manager.repository;

import com.middleware.manager.domain.AdminAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminAccountMapper {

    AdminAccount findById(@Param("id") Long id);

    AdminAccount findByUsername(@Param("username") String username);

    List<AdminAccount> findAllByOrderByCreatedAtAsc();

    long countByRole(@Param("role") String role);

    int insert(AdminAccount account);

    int update(AdminAccount account);

    int deleteById(@Param("id") Long id);

    long count();
}
