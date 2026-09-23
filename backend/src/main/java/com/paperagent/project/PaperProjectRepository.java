package com.paperagent.project;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 数据访问层。Spring Data JPA 会自动实现常用的增删改查方法。
 */
public interface PaperProjectRepository extends JpaRepository<PaperProject, String> {
}

