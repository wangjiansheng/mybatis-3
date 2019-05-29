/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.autoconstructor;

import org.apache.ibatis.BaseDataTest;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AutoConstructorTest {
  private static SqlSessionFactory sqlSessionFactory;

  @BeforeAll
  static void setUp() throws Exception {
    // create a SqlSessionFactory
    try (Reader reader = Resources.getResourceAsReader("org/apache/ibatis/autoconstructor/mybatis-config.xml")) {
      sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);
    }

    // populate in-memory database  填充内存数据库
    BaseDataTest.runScript(sqlSessionFactory.getConfiguration().getEnvironment().getDataSource(),
        "org/apache/ibatis/autoconstructor/CreateDB.sql");
  }

  @Test//查询数据
  void fullyPopulatedSubject() {
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      //mapper 为代理类  MapperProxyFactory类中由jdk动态代理生成
      final AutoConstructorMapper mapper = sqlSession.getMapper(AutoConstructorMapper.class);
      final Object subject = mapper.getSubject(1);
      assertNotNull(subject);
    }
  }

  @Test  //测试报异常   原始构造器
  void primitiveSubjects() {
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      final AutoConstructorMapper mapper = sqlSession.getMapper(AutoConstructorMapper.class);
//      (1, 'a', 10, 100, 45, 1, CURRENT_TIMESTAMP),
//      (2, 'b', 10, NULL, 45, 1, CURRENT_TIMESTAMP),
//      (2, 'c', 10, NULL, NULL, 0, CURRENT_TIMESTAMP);

      //报异常原因是因为 int字段映射了null
      assertThrows(PersistenceException.class, mapper::getSubjects);
    }
  }

  @Test  //测试注解了的构造器
  void annotatedSubject() {
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      final AutoConstructorMapper mapper = sqlSession.getMapper(AutoConstructorMapper.class);
      //判断是否为空和长度是否和查出来的数据量一样
      verifySubjects(mapper.getAnnotatedSubjects());
    }
  }

  @Test //Height 属性错误
  void badSubject() {
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      final AutoConstructorMapper mapper = sqlSession.getMapper(AutoConstructorMapper.class);
      assertThrows(PersistenceException.class, mapper::getBadSubjects);
    }
  }

  @Test// 枚举对映射  TestEnum
  void extensiveSubject() {
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      final AutoConstructorMapper mapper = sqlSession.getMapper(AutoConstructorMapper.class);
      verifySubjects(mapper.getExtensiveSubject());
    }
  }

  private void verifySubjects(final List<?> subjects) {
    assertNotNull(subjects);
    Assertions.assertThat(subjects.size()).isEqualTo(3);
  }
}
