/**
 * Copyright 2009-2019 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.binding;

import javassist.util.proxy.Proxy;
import net.sf.cglib.proxy.Factory;
import org.apache.ibatis.BaseDataTest;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.domain.blog.*;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.executor.result.DefaultResultHandler;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

import static com.googlecode.catchexception.apis.BDDCatchException.caughtException;
import static com.googlecode.catchexception.apis.BDDCatchException.when;
import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.jupiter.api.Assertions.*;

class BindingTest {
  private static SqlSessionFactory sqlSessionFactory;

  @BeforeAll
  static void setup() throws Exception {
    //创建derby数据库
    DataSource dataSource = BaseDataTest.createBlogDataSource();
    BaseDataTest.runScript(dataSource, BaseDataTest.BLOG_DDL);
    BaseDataTest.runScript(dataSource, BaseDataTest.BLOG_DATA);

    TransactionFactory transactionFactory = new JdbcTransactionFactory();
    Environment environment = new Environment("Production", transactionFactory, dataSource);
    Configuration configuration = new Configuration(environment);
    configuration.setLazyLoadingEnabled(true);
    configuration.setUseActualParamName(false); // to test legacy style reference (#{0} #{1})
    //注册别名---映射的时候用
    configuration.getTypeAliasRegistry().registerAlias(Blog.class);
    configuration.getTypeAliasRegistry().registerAlias(Post.class);
    configuration.getTypeAliasRegistry().registerAlias(Author.class);
    configuration.addMapper(BoundBlogMapper.class);
    configuration.addMapper(BoundAuthorMapper.class);
    //读取配置文件信息（全局配置文件和映射文件），构造出SqlSessionFactory
    sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
  }

  @Test
  void shouldSelectBlogWithPostsUsingSubSelect() {
    //数据流会在 try 执行完毕后自动被关闭，前提是，这些可关闭的资源必须实现 java.lang.AutoCloseable 接口。
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      Blog b = mapper.selectBlogWithPostsUsingSubSelect(1);
      assertEquals(1, b.getId());
      assertNotNull(b.getAuthor());
      assertEquals(101, b.getAuthor().getId());
      assertEquals("jim", b.getAuthor().getUsername());
      assertEquals("********", b.getAuthor().getPassword());
      assertEquals(2, b.getPosts().size());
    }
  }

  @Test
//参数用 List
  void shouldFindPostsInList() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundAuthorMapper mapper = session.getMapper(BoundAuthorMapper.class);
      List<Post> posts = mapper.findPostsInList(new ArrayList<Integer>() {{
        add(1);
        add(3);
        add(5);
      }});
      assertEquals(3, posts.size());
      session.rollback();
    }
  }

  @Test
//参数用 数组[]
  void shouldFindPostsInArray() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundAuthorMapper mapper = session.getMapper(BoundAuthorMapper.class);
      Integer[] params = new Integer[]{1, 3, 5};
      List<Post> posts = mapper.findPostsInArray(params);
      assertEquals(3, posts.size());
      session.rollback();
    }
  }

  @Test
// 用RowBounds分页   @parma   参数位置绑定
  void shouldFindThreeSpecificPosts() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundAuthorMapper mapper = session.getMapper(BoundAuthorMapper.class);
      List<Post> posts = mapper.findThreeSpecificPosts(1, new RowBounds(1, 1), 3, 5);
      assertEquals(1, posts.size());
      assertEquals(3, posts.get(0).getId());
      session.rollback();
    }
  }

  @Test
  void shouldInsertAuthorWithSelectKey() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundAuthorMapper mapper = session.getMapper(BoundAuthorMapper.class);
      Author author = new Author(-1, "cbegin", "******", "cbegin@nowhere.com", "N/A", Section.NEWS);
      int rows = mapper.insertAuthor(author);
      assertEquals(1, rows);
      session.rollback();
    }
  }

  @Test
  void verifyErrorMessageFromSelectKey() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      try {
        BoundAuthorMapper mapper = session.getMapper(BoundAuthorMapper.class);
        Author author = new Author(-1, "cbegin", "******", "cbegin@nowhere.com", "N/A", Section.NEWS);
        when(mapper).insertAuthorInvalidSelectKey(author);
        then(caughtException()).isInstanceOf(PersistenceException.class).hasMessageContaining(
          "### The error may exist in org/apache/ibatis/binding/BoundAuthorMapper.xml" + System.lineSeparator() +
            "### The error may involve org.apache.ibatis.binding.BoundAuthorMapper.insertAuthorInvalidSelectKey!selectKey" + System.lineSeparator() +
            "### The error occurred while executing a query");
      } finally {
        session.rollback();
      }
    }
  }

  @Test
  void verifyErrorMessageFromInsertAfterSelectKey() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      try {
        BoundAuthorMapper mapper = session.getMapper(BoundAuthorMapper.class);
        Author author = new Author(-1, "cbegin", "******", "cbegin@nowhere.com", "N/A", Section.NEWS);
        when(mapper).insertAuthorInvalidInsert(author);
        then(caughtException()).isInstanceOf(PersistenceException.class).hasMessageContaining(
          "### The error may exist in org/apache/ibatis/binding/BoundAuthorMapper.xml" + System.lineSeparator() +
            "### The error may involve org.apache.ibatis.binding.BoundAuthorMapper.insertAuthorInvalidInsert" + System.lineSeparator() +
            "### The error occurred while executing an update");
      } finally {
        session.rollback();
      }
    }
  }

  @Test
  void shouldInsertAuthorWithSelectKeyAndDynamicParams() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundAuthorMapper mapper = session.getMapper(BoundAuthorMapper.class);
      Author author = new Author(-1, "cbegin", "******", "cbegin@nowhere.com", "N/A", Section.NEWS);
      int rows = mapper.insertAuthorDynamic(author);
      assertEquals(1, rows);
      assertFalse(-1 == author.getId()); // id must be autogenerated
      Author author2 = mapper.selectAuthor(author.getId());
      assertNotNull(author2);
      assertEquals(author.getEmail(), author2.getEmail());
      session.rollback();
    }
  }

  @Test
  void shouldSelectRandom() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      Integer x = mapper.selectRandom();
      System.out.println(x);
      assertNotNull(x);
    }
  }

  @Test
  void shouldExecuteBoundSelectListOfBlogsStatement() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      List<Blog> blogs = mapper.selectBlogs();
      System.out.println(blogs);
      assertEquals(2, blogs.size());
    }
  }

  @Test
//数据封装成map,id作为map的key
  void shouldExecuteBoundSelectMapOfBlogsById() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      Map<Integer, Blog> blogs = mapper.selectBlogsAsMapById();
      System.out.println(blogs);
      assertEquals(2, blogs.size());
      for (Map.Entry<Integer, Blog> blogEntry : blogs.entrySet()) {
        assertEquals(blogEntry.getKey(), (Integer) blogEntry.getValue().getId());
      }
    }
  }

  @Test
  void shouldExecuteMultipleBoundSelectOfBlogsByIdInWithProvidedResultHandlerBetweenSessions() {
    final DefaultResultHandler handler = new DefaultResultHandler();
    try (SqlSession session = sqlSessionFactory.openSession()) {
      session.select("selectBlogsAsMapById", handler);
    }
    //session 不相同
    final DefaultResultHandler moreHandler = new DefaultResultHandler();
    try (SqlSession session = sqlSessionFactory.openSession()) {
      session.select("selectBlogsAsMapById", moreHandler);
    }
    System.out.println(handler.getResultList());
    System.out.println(moreHandler.getResultList());
    assertEquals(2, handler.getResultList().size());
    assertEquals(2, moreHandler.getResultList().size());
  }

  @Test
  void shouldExecuteMultipleBoundSelectOfBlogsByIdInWithProvidedResultHandlerInSameSession() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      final DefaultResultHandler handler = new DefaultResultHandler();
      session.select("selectBlogsAsMapById", handler);
      //session 相同
      final DefaultResultHandler moreHandler = new DefaultResultHandler();
      session.select("selectBlogsAsMapById", moreHandler);

      assertEquals(2, handler.getResultList().size());
      assertEquals(2, moreHandler.getResultList().size());
    }
  }

  @Test//同一个session  使用缓存
  void shouldExecuteMultipleBoundSelectMapOfBlogsByIdInSameSessionWithoutClearingLocalCache() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      Map<Integer, Blog> blogs = mapper.selectBlogsAsMapById();
      Map<Integer, Blog> moreBlogs = mapper.selectBlogsAsMapById();
      assertEquals(2, blogs.size());
      assertEquals(2, moreBlogs.size());
      for (Map.Entry<Integer, Blog> blogEntry : blogs.entrySet()) {
        assertEquals(blogEntry.getKey(), (Integer) blogEntry.getValue().getId());
      }
      for (Map.Entry<Integer, Blog> blogEntry : moreBlogs.entrySet()) {
        assertEquals(blogEntry.getKey(), (Integer) blogEntry.getValue().getId());
      }
    }
  }

  @Test//不同的session 使用缓存sql只查询一次  使用全局缓存
  void shouldExecuteMultipleBoundSelectMapOfBlogsByIdBetweenTwoSessionsWithGlobalCacheEnabled() {
    Map<Integer, Blog> blogs;//默认autoCommit=false
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      blogs = mapper.selectBlogsAsMapById();
    }

    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      Map<Integer, Blog> moreBlogs = mapper.selectBlogsAsMapById();
      assertEquals(2, blogs.size());
      assertEquals(2, moreBlogs.size());
      for (Map.Entry<Integer, Blog> blogEntry : blogs.entrySet()) {
        assertEquals(blogEntry.getKey(), (Integer) blogEntry.getValue().getId());
      }
      for (Map.Entry<Integer, Blog> blogEntry : moreBlogs.entrySet()) {
        assertEquals(blogEntry.getKey(), (Integer) blogEntry.getValue().getId());
      }
    }
  }

  @Test//使用xml配置sql
  void shouldSelectListOfBlogsUsingXMLConfig() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      List<Blog> blogs = mapper.selectBlogsFromXML();
      assertEquals(2, blogs.size());
    }
  }

  @Test//使用@SelectProvider注解
  void shouldExecuteBoundSelectListOfBlogsStatementUsingProvider() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      List<Blog> blogs = mapper.selectBlogsUsingProvider();
      assertEquals(2, blogs.size());
    }
  }

  @Test//返回List<Map<String,Object>>
  void shouldExecuteBoundSelectListOfBlogsAsMaps() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      List<Map<String, Object>> blogs = mapper.selectBlogsAsMaps();
      System.out.println(blogs);
      assertEquals(2, blogs.size());
    }
  }

  @Test//like
  void shouldSelectListOfPostsLike() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      List<Post> posts = mapper.selectPostsLike(new RowBounds(1, 1), "%a%");
      assertEquals(1, posts.size());
    }
  }

  @Test //多个参数
  void shouldSelectListOfPostsLikeTwoParameters() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      List<Post> posts = mapper.selectPostsLikeSubjectAndBody(new RowBounds(1, 1), "%a%", "%a%");
      assertEquals(1, posts.size());
    }
  }

  @Test
  void shouldExecuteBoundSelectOneBlogStatement() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      Blog blog = mapper.selectBlog(1);
      assertEquals(1, blog.getId());
      assertEquals("Jim Business", blog.getTitle());
    }
  }

  @Test  //@ConstructorArgs实现字段映射
  void shouldExecuteBoundSelectOneBlogStatementWithConstructor() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      Blog blog = mapper.selectBlogUsingConstructor(1);
      assertEquals(1, blog.getId());
      assertEquals("Jim Business", blog.getTitle());
      assertNotNull(blog.getAuthor(), "author should not be null");
      List<Post> posts = blog.getPosts();
      assertTrue(posts != null && !posts.isEmpty(), "posts should not be empty");
    }
  }

  @Test //xml中配置 resultMap -》Constructor
  void shouldExecuteBoundSelectBlogUsingConstructorWithResultMap() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      Blog blog = mapper.selectBlogUsingConstructorWithResultMap(1);
      assertEquals(1, blog.getId());
      assertEquals("Jim Business", blog.getTitle());
      assertNotNull(blog.getAuthor(), "author should not be null");
      List<Post> posts = blog.getPosts();
      assertTrue(posts != null && !posts.isEmpty(), "posts should not be empty");
    }
  }

  @Test   //映射到另一个文件的resultMap
  void shouldExecuteBoundSelectBlogUsingConstructorWithResultMapAndProperties() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      Blog blog = mapper.selectBlogUsingConstructorWithResultMapAndProperties(1);
      assertEquals(1, blog.getId());
      assertEquals("Jim Business", blog.getTitle());
      assertNotNull(blog.getAuthor(), "author should not be null");
      Author author = blog.getAuthor();
      assertEquals(101, author.getId());
      assertEquals("jim@ibatis.apache.org", author.getEmail());
      assertEquals("jim", author.getUsername());
      assertEquals(Section.NEWS, author.getFavouriteSection());
      List<Post> posts = blog.getPosts();
      assertNotNull(posts, "posts should not be empty");
      assertEquals(2, posts.size());
    }
  }

  @Disabled
  @Test
    // issue #480 and #101  映射到另一个文件，包含集合collection
  void shouldExecuteBoundSelectBlogUsingConstructorWithResultMapCollection() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      Blog blog = mapper.selectBlogUsingConstructorWithResultMapCollection(1);
      assertEquals(1, blog.getId());
      assertEquals("Jim Business", blog.getTitle());
      assertNotNull(blog.getAuthor(), "author should not be null");
      List<Post> posts = blog.getPosts();
      assertTrue(posts != null && !posts.isEmpty(), "posts should not be empty");
    }
  }

  @Test//  会一起查询 其他mapper
  void shouldExecuteBoundSelectOneBlogStatementWithConstructorUsingXMLConfig() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      Blog blog = mapper.selectBlogByIdUsingConstructor(1);
      System.out.println(blog);
      assertEquals(1, blog.getId());
      assertEquals("Jim Business", blog.getTitle());
      assertNotNull(blog.getAuthor(), "author should not be null");
      List<Post> posts = blog.getPosts();
      assertTrue(posts != null && !posts.isEmpty(), "posts should not be empty");
    }
  }

  @Test //map 作为参数，返回map
  void shouldSelectOneBlogAsMap() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      Map<String, Object> blog = mapper.selectBlogAsMap(new HashMap<String, Object>() {
        {
          put("id", 1);
        }
      });
      System.out.println(blog);
      assertEquals(1, blog.get("ID"));
      assertEquals("Jim Business", blog.get("TITLE"));
    }
  }

  @Test
  void shouldSelectOneAuthor() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundAuthorMapper mapper = session.getMapper(BoundAuthorMapper.class);
      Author author = mapper.selectAuthor(101);
      assertEquals(101, author.getId());
      assertEquals("jim", author.getUsername());
      assertEquals("********", author.getPassword());
      assertEquals("jim@ibatis.apache.org", author.getEmail());
      assertEquals("", author.getBio());
    }
  }

  @Test//测试缓存
  void shouldSelectOneAuthorFromCache() {
    Author author1 = selectOneAuthor();
    Author author2 = selectOneAuthor();
    assertSame(author1, author2, "Same (cached) instance should be returned unless rollback is called.");
  }

  private Author selectOneAuthor() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundAuthorMapper mapper = session.getMapper(BoundAuthorMapper.class);
      return mapper.selectAuthor(101);
    }
  }

  @Test  // 构造器注入测试
  void shouldSelectOneAuthorByConstructor() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundAuthorMapper mapper = session.getMapper(BoundAuthorMapper.class);
      Author author = mapper.selectAuthorConstructor(101);
      assertEquals(101, author.getId());
      assertEquals("jim", author.getUsername());
      assertEquals("********", author.getPassword());
      assertEquals("jim@ibatis.apache.org", author.getEmail());
      assertEquals("", author.getBio());
    }
  }

  @Test//数据库包含draft字段，更加draft值封装成不同的实体返回
  void shouldSelectDraftTypedPosts() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      List<Post> posts = mapper.selectPosts();
      System.out.println(posts);
      assertEquals(5, posts.size());

      //数据库中 1、3条数据draft=1  ，将等于1的封装成DraftPost
      assertTrue(posts.get(0) instanceof DraftPost);
      assertFalse(posts.get(1) instanceof DraftPost);
      assertTrue(posts.get(2) instanceof DraftPost);
      assertFalse(posts.get(3) instanceof DraftPost);
      assertFalse(posts.get(4) instanceof DraftPost);
    }
  }

  @Test
  void shouldSelectDraftTypedPostsWithResultMap() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      List<Post> posts = mapper.selectPostsWithResultMap();
      System.out.println(posts);
      assertEquals(5, posts.size());
      assertTrue(posts.get(0) instanceof DraftPost);
      assertFalse(posts.get(1) instanceof DraftPost);
      assertTrue(posts.get(2) instanceof DraftPost);
      assertFalse(posts.get(3) instanceof DraftPost);
      assertFalse(posts.get(4) instanceof DraftPost);
    }
  }

  @Test
  void shouldReturnANotNullToString() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      assertNotNull(mapper.toString());
    }
  }

  @Test
  void shouldReturnANotNullHashCode() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      assertNotNull(mapper.hashCode());
    }
  }

  @Test
  void shouldCompareTwoMappers() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      BoundBlogMapper mapper2 = session.getMapper(BoundBlogMapper.class);
      assertNotEquals(mapper, mapper2);
    }
  }

  @Test//不存在参数报错
  void shouldFailWhenSelectingOneBlogWithNonExistentParam() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      assertThrows(Exception.class, () -> mapper.selectBlogByNonExistentParam(1));
    }
  }

  @Test//参数null,报错
  void shouldFailWhenSelectingOneBlogWithNullParam() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);

      assertThrows(Exception.class, () -> mapper.selectBlogByNullParam(null));
    }
  }

  @Test
    // Decided that maps are dynamic so no existent params do not fail
  //决定映射是动态的，因此不存在params不会失败   ,用map作为参数
  void shouldFailWhenSelectingOneBlogWithNonExistentNestedParam() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      mapper.selectBlogByNonExistentNestedParam(1, Collections.<String, Object>emptyMap());
    }
  }

  @Test//参数位置作为传参参数  #{0} AND title = #{1}"
  void shouldSelectBlogWithDefault30ParamNames() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      Blog blog = mapper.selectBlogByDefault30ParamNames(1, "Jim Business");
      assertNotNull(blog);
    }
  }

  @Test//#{param1} AND title = #{param2}
  void shouldSelectBlogWithDefault31ParamNames() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      Blog blog = mapper.selectBlogByDefault31ParamNames(1, "Jim Business");
      assertNotNull(blog);
    }
  }

  @Test//(@Param("column")
  void shouldSelectBlogWithAParamNamedValue() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      Blog blog = mapper.selectBlogWithAParamNamedValue("id", 1, "Jim Business");
      assertNotNull(blog);
    }
  }

  @Test //缓存MapperMethod
  void shouldCacheMapperMethod() throws Exception {
    try (SqlSession session = sqlSessionFactory.openSession()) {

      // Create another mapper instance with a method cache we can test against:创建另一个映射器实例的方法缓存，我们可以测试:
      final MapperProxyFactory<BoundBlogMapper> mapperProxyFactory = new MapperProxyFactory<BoundBlogMapper>(BoundBlogMapper.class);
      assertEquals(BoundBlogMapper.class, mapperProxyFactory.getMapperInterface());
      final BoundBlogMapper mapper = mapperProxyFactory.newInstance(session);
      //动态代理获取mapper
      assertNotSame(mapper, mapperProxyFactory.newInstance(session));
      assertTrue(mapperProxyFactory.getMethodCache().isEmpty());

      // Mapper methods we will call later:我们稍后将调用Mapper方法
      final Method selectBlog = BoundBlogMapper.class.getMethod("selectBlog", Integer.TYPE);
      final Method selectBlogByIdUsingConstructor = BoundBlogMapper.class.getMethod("selectBlogByIdUsingConstructor", Integer.TYPE);

      // Call mapper method and verify it is cached:调用mapper方法并验证它被缓存:
      assertEquals(0, mapperProxyFactory.getMethodCache().size());
      mapper.selectBlog(1);//方法调用
      assertEquals(1, mapperProxyFactory.getMethodCache().size());//缓存比较
      assertTrue(mapperProxyFactory.getMethodCache().containsKey(selectBlog));//缓存是否包含比较
      final MapperMethod cachedSelectBlog = mapperProxyFactory.getMethodCache().get(selectBlog);

      // Call mapper method again and verify the cache is unchanged:
      //再次调用mapper方法，验证缓存是否不变:   结果:不变
      session.clearCache();
      mapper.selectBlog(1);
      assertEquals(1, mapperProxyFactory.getMethodCache().size());
      assertSame(cachedSelectBlog, mapperProxyFactory.getMethodCache().get(selectBlog));

      // Call another mapper method and verify that it shows up in the cache as well:
      //调用另一个映射器方法，并验证它也出现在缓存中:   结果：出现在缓存中了
      session.clearCache();
      mapper.selectBlogByIdUsingConstructor(1);
      assertEquals(2, mapperProxyFactory.getMethodCache().size());
      assertSame(cachedSelectBlog, mapperProxyFactory.getMethodCache().get(selectBlog));
      assertTrue(mapperProxyFactory.getMethodCache().containsKey(selectBlogByIdUsingConstructor));
    }
  }

  @Test  //使用@Results，查询 Posts   ，默认使用懒加载
  void shouldGetBlogsWithAuthorsAndPosts() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      List<Blog> blogs = mapper.selectBlogsWithAutorAndPosts();
      assertEquals(2, blogs.size());
      assertTrue(blogs.get(0) instanceof Proxy);
      assertEquals(101, blogs.get(0).getAuthor().getId());
      assertEquals(1, blogs.get(0).getPosts().size());
      assertEquals(1, blogs.get(0).getPosts().get(0).getId());
      assertTrue(blogs.get(1) instanceof Proxy);
      assertEquals(102, blogs.get(1).getAuthor().getId());
      assertEquals(1, blogs.get(1).getPosts().size());
      assertEquals(2, blogs.get(1).getPosts().get(0).getId());
    }
  }

  @Test//使用@Results，查询 Posts   ，默认使用懒加载  ，这里马上加载，sql立即执行
  void shouldGetBlogsWithAuthorsAndPostsEagerly() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      List<Blog> blogs = mapper.selectBlogsWithAutorAndPostsEagerly();
      assertEquals(2, blogs.size());
      assertFalse(blogs.get(0) instanceof Factory);
      assertEquals(101, blogs.get(0).getAuthor().getId());
      assertEquals(1, blogs.get(0).getPosts().size());
      assertEquals(1, blogs.get(0).getPosts().get(0).getId());
      assertFalse(blogs.get(1) instanceof Factory);
      assertEquals(102, blogs.get(1).getAuthor().getId());
      assertEquals(1, blogs.get(1).getPosts().size());
      assertEquals(2, blogs.get(1).getPosts().get(0).getId());
    }
  }

  @Test//将结果封装到 resultHandler  RowBounds:分页
  void executeWithResultHandlerAndRowBounds() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      final DefaultResultHandler handler = new DefaultResultHandler();
      mapper.collectRangeBlogs(handler, new RowBounds(1, 1));

      assertEquals(1, handler.getResultList().size());
      Blog blog = (Blog) handler.getResultList().get(0);
      assertEquals(2, blog.getId());
    }
  }

  @Test// @MapKey("id")   返回map
  void executeWithMapKeyAndRowBounds() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      Map<Integer, Blog> blogs = mapper.selectRangeBlogsAsMapById(new RowBounds(1, 1));

      assertEquals(1, blogs.size());
      Blog blog = blogs.get(2);
      assertEquals(2, blog.getId());
    }
  }

  @Test //使用Cursor（光标）
  void executeWithCursorAndRowBounds() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      BoundBlogMapper mapper = session.getMapper(BoundBlogMapper.class);
      try (Cursor<Blog> blogs = mapper.openRangeBlogs(new RowBounds(1, 1))) {
        Iterator<Blog> blogIterator = blogs.iterator();
        Blog blog = blogIterator.next();
        assertEquals(2, blog.getId());
        assertFalse(blogIterator.hasNext());
      }
    } catch (IOException e) {
      Assertions.fail(e.getMessage());
    }
  }

  @Test  //获取mapper（即dao层）  前面注册了2个
    //configuration.addMapper(BoundBlogMapper.class);
    //  configuration.addMapper(BoundAuthorMapper.class);
  void registeredMappers() {
    Collection<Class<?>> mapperClasses = sqlSessionFactory.getConfiguration().getMapperRegistry().getMappers();
    assertEquals(2, mapperClasses.size());
    assertTrue(mapperClasses.contains(BoundBlogMapper.class));
    assertTrue(mapperClasses.contains(BoundAuthorMapper.class));
  }

}
