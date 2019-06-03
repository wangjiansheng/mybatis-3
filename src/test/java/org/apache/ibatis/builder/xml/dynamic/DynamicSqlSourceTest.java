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
package org.apache.ibatis.builder.xml.dynamic;

import org.apache.ibatis.BaseDataTest;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.scripting.xmltags.*;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.sql.SQLException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

//动态sql测试
class DynamicSqlSourceTest extends BaseDataTest {

  @Test  //1个SqlNode不带参数
  void shouldDemonstrateSimpleExpectedTextWithNoLoopsOrConditionals() throws Exception {
    final String expected = "SELECT * FROM BLOG";
    //创建sqlnode
    final MixedSqlNode sqlNode = mixedContents(new TextSqlNode(expected));
    //MixedSqlNode
    //将sqlnode set进 DynamicSqlSource
    DynamicSqlSource source = createDynamicSqlSource(sqlNode);
    //获取boundSql
    BoundSql boundSql = source.getBoundSql(null);
    assertEquals(expected, boundSql.getSql());
  }

  @Test//多个SqlNode
  void shouldDemonstrateMultipartExpectedTextWithNoLoopsOrConditionals() throws Exception {
    final String expected = "SELECT * FROM BLOG WHERE ID = ?";
    DynamicSqlSource source = createDynamicSqlSource(
        new TextSqlNode("SELECT * FROM BLOG"),
        new TextSqlNode("WHERE ID = ?"));
    BoundSql boundSql = source.getBoundSql(null);
    assertEquals(expected, boundSql.getSql());
  }

  @Test  //IfSqlNode test=true 测试
  void shouldConditionallyIncludeWhere() throws Exception {
    final String expected = "SELECT * FROM BLOG WHERE ID = ?";
    DynamicSqlSource source = createDynamicSqlSource(
        new TextSqlNode("SELECT * FROM BLOG"),
        new IfSqlNode(mixedContents(new TextSqlNode("WHERE ID = ?")), "true"
        ));
    BoundSql boundSql = source.getBoundSql(null);
    assertEquals(expected, boundSql.getSql());
  }

  @Test  //IfSqlNode test=false 测试
  void shouldConditionallyExcludeWhere() throws Exception {
    final String expected = "SELECT * FROM BLOG";
    DynamicSqlSource source = createDynamicSqlSource(
        new TextSqlNode("SELECT * FROM BLOG"),
        new IfSqlNode(mixedContents(new TextSqlNode("WHERE ID = ?")), "false"
        ));
    BoundSql boundSql = source.getBoundSql(null);
    assertEquals(expected, boundSql.getSql());
  }

  @Test//ChooseSqlNode 测试  IfSqlNode不通过使用默认的
  void shouldConditionallyDefault() throws Exception {
    final String expected = "SELECT * FROM BLOG WHERE CATEGORY = 'DEFAULT'";
    DynamicSqlSource source = createDynamicSqlSource(
        new TextSqlNode("SELECT * FROM BLOG"),
        new ChooseSqlNode(new ArrayList<SqlNode>() {{
          add(new IfSqlNode(mixedContents(new TextSqlNode("WHERE CATEGORY = ?")), "false"
          ));
          add(new IfSqlNode(mixedContents(new TextSqlNode("WHERE CATEGORY = 'NONE'")), "false"
          ));
        }}, mixedContents(new TextSqlNode("WHERE CATEGORY = 'DEFAULT'"))));
    BoundSql boundSql = source.getBoundSql(null);
    assertEquals(expected, boundSql.getSql());
  }

  @Test////ChooseSqlNode 测试
  void shouldConditionallyChooseFirst() throws Exception {
    final String expected = "SELECT * FROM BLOG WHERE CATEGORY = ?";
    DynamicSqlSource source = createDynamicSqlSource(
        new TextSqlNode("SELECT * FROM BLOG"),
        new ChooseSqlNode(new ArrayList<SqlNode>() {{
          add(new IfSqlNode(mixedContents(new TextSqlNode("WHERE CATEGORY = ?")), "true"
          ));
          add(new IfSqlNode(mixedContents(new TextSqlNode("WHERE CATEGORY = 'NONE'")), "false"
          ));
        }}, mixedContents(new TextSqlNode("WHERE CATEGORY = 'DEFAULT'"))));
    BoundSql boundSql = source.getBoundSql(null);
    assertEquals(expected, boundSql.getSql());
  }

  @Test
  void shouldConditionallyChooseSecond() throws Exception {
    final String expected = "SELECT * FROM BLOG WHERE CATEGORY = 'NONE'";
    DynamicSqlSource source = createDynamicSqlSource(
        new TextSqlNode("SELECT * FROM BLOG"),
        new ChooseSqlNode(new ArrayList<SqlNode>() {{
          add(new IfSqlNode(mixedContents(new TextSqlNode("WHERE CATEGORY = ?")), "false"
          ));
          add(new IfSqlNode(mixedContents(new TextSqlNode("WHERE CATEGORY = 'NONE'  ")), "true"
          ));
        }}, mixedContents(new TextSqlNode("WHERE CATEGORY = 'DEFAULT'"))));
    BoundSql boundSql = source.getBoundSql(null);
    assertEquals(expected, boundSql.getSql());
  }

  @Test// WhereSqlNode  自动添加where   而且去掉多余的and
  void shouldTrimWHEREInsteadOfANDForFirstCondition() throws Exception {
    final String expected = "SELECT * FROM BLOG WHERE  ID = ?";
    DynamicSqlSource source = createDynamicSqlSource(
        new TextSqlNode("SELECT * FROM BLOG"),
        new WhereSqlNode(new Configuration(),mixedContents(
            new IfSqlNode(mixedContents(new TextSqlNode("   and ID = ?  ")), "true"
            ),
            new IfSqlNode(mixedContents(new TextSqlNode("   or NAME = ?  ")), "false"
            )
        )));
    BoundSql boundSql = source.getBoundSql(null);
    assertEquals(expected, boundSql.getSql());
  }

  @Test// WhereSqlNode  自动添加where   而且去掉多余的 and 和保留 LF 换行
  void shouldTrimWHEREANDWithLFForFirstCondition() throws Exception {
    final String expected = "SELECT * FROM BLOG WHERE \n ID = ?";
    DynamicSqlSource source = createDynamicSqlSource(
        new TextSqlNode("SELECT * FROM BLOG"),
        new WhereSqlNode(new Configuration(),mixedContents(
            new IfSqlNode(mixedContents(new TextSqlNode("   and\n ID = ?  ")), "true"
                )
            )));
    BoundSql boundSql = source.getBoundSql(null);
    assertEquals(expected, boundSql.getSql());
  }

  @Test//WhereSqlNode  自动添加where   而且去掉多余的 and 和保留 CRLF 回车换行
  void shouldTrimWHEREANDWithCRLFForFirstCondition() throws Exception {
    final String expected = "SELECT * FROM BLOG WHERE \r\n ID = ?";
    DynamicSqlSource source = createDynamicSqlSource(
        new TextSqlNode("SELECT * FROM BLOG"),
        new WhereSqlNode(new Configuration(),mixedContents(
            new IfSqlNode(mixedContents(new TextSqlNode("   and\r\n ID = ?  ")), "true"
                )
            )));
    BoundSql boundSql = source.getBoundSql(null);
    assertEquals(expected, boundSql.getSql());
  }

  @Test//tob
  void shouldTrimWHEREANDWithTABForFirstCondition() throws Exception {
    final String expected = "SELECT * FROM BLOG WHERE \t ID = ?";
    DynamicSqlSource source = createDynamicSqlSource(
        new TextSqlNode("SELECT * FROM BLOG"),
        new WhereSqlNode(new Configuration(),mixedContents(
            new IfSqlNode(mixedContents(new TextSqlNode("   and\t ID = ?  ")), "true"
                )
            )));
    BoundSql boundSql = source.getBoundSql(null);
    assertEquals(expected, boundSql.getSql());
  }

  @Test // or
  void shouldTrimWHEREORWithLFForFirstCondition() throws Exception {
    final String expected = "SELECT * FROM BLOG WHERE \n ID = ?";
    DynamicSqlSource source = createDynamicSqlSource(
        new TextSqlNode("SELECT * FROM BLOG"),
        new WhereSqlNode(new Configuration(),mixedContents(
            new IfSqlNode(mixedContents(new TextSqlNode("   or\n ID = ?  ")), "true"
                )
            )));
    BoundSql boundSql = source.getBoundSql(null);
    assertEquals(expected, boundSql.getSql());
  }

  @Test //or
  void shouldTrimWHEREORWithCRLFForFirstCondition() throws Exception {
    final String expected = "SELECT * FROM BLOG WHERE \r\n ID = ?";
    DynamicSqlSource source = createDynamicSqlSource(
        new TextSqlNode("SELECT * FROM BLOG"),
        new WhereSqlNode(new Configuration(),mixedContents(
            new IfSqlNode(mixedContents(new TextSqlNode("   or\r\n ID = ?  ")), "true"
                )
            )));
    BoundSql boundSql = source.getBoundSql(null);
    assertEquals(expected, boundSql.getSql());
  }

  @Test//or
  void shouldTrimWHEREORWithTABForFirstCondition() throws Exception {
    final String expected = "SELECT * FROM BLOG WHERE \t ID = ?";
    DynamicSqlSource source = createDynamicSqlSource(
        new TextSqlNode("SELECT * FROM BLOG"),
        new WhereSqlNode(new Configuration(),mixedContents(
            new IfSqlNode(mixedContents(new TextSqlNode("   or\t ID = ?  ")), "true"
                )
            )));
    BoundSql boundSql = source.getBoundSql(null);
    assertEquals(expected, boundSql.getSql());
  }

  @Test
  void shouldTrimWHEREInsteadOfORForSecondCondition() throws Exception {
    final String expected = "SELECT * FROM BLOG WHERE  NAME = ?";
    DynamicSqlSource source = createDynamicSqlSource(
        new TextSqlNode("SELECT * FROM BLOG"),
        new WhereSqlNode(new Configuration(),mixedContents(
            new IfSqlNode(mixedContents(new TextSqlNode("   and ID = ?  ")), "false"
            ),
            new IfSqlNode(mixedContents(new TextSqlNode("   or NAME = ?  ")), "true"
            )
        )));
    BoundSql boundSql = source.getBoundSql(null);
    assertEquals(expected, boundSql.getSql());
  }

  @Test
  void shouldTrimWHEREInsteadOfANDForBothConditions() throws Exception {
    final String expected = "SELECT * FROM BLOG WHERE  ID = ?   OR NAME = ?";
    DynamicSqlSource source = createDynamicSqlSource(
        new TextSqlNode("SELECT * FROM BLOG"),
        new WhereSqlNode(new Configuration(),mixedContents(
            new IfSqlNode(mixedContents(new TextSqlNode("   and ID = ?   ")), "true"
            ),
            new IfSqlNode(mixedContents(new TextSqlNode("OR NAME = ?  ")), "true"
            )
        )));
    BoundSql boundSql = source.getBoundSql(null);
    assertEquals(expected, boundSql.getSql());
  }

  @Test
  void shouldTrimNoWhereClause() throws Exception {
    final String expected = "SELECT * FROM BLOG";
    DynamicSqlSource source = createDynamicSqlSource(
        new TextSqlNode("SELECT * FROM BLOG"),
        new WhereSqlNode(new Configuration(),mixedContents(
            new IfSqlNode(mixedContents(new TextSqlNode("   and ID = ?   ")), "false"
            ),
            new IfSqlNode(mixedContents(new TextSqlNode("OR NAME = ?  ")), "false"
            )
        )));
    BoundSql boundSql = source.getBoundSql(null);
    assertEquals(expected, boundSql.getSql());
  }

  @Test//SetSqlNode
  void shouldTrimSETInsteadOfCOMMAForBothConditions() throws Exception {
    final String expected = "UPDATE BLOG SET ID = ?,  NAME = ?";
    DynamicSqlSource source = createDynamicSqlSource(
        new TextSqlNode("UPDATE BLOG"),
        new SetSqlNode(new Configuration(),mixedContents(
            new IfSqlNode(mixedContents(new TextSqlNode(" ID = ?, ")), "true"
            ),
            new IfSqlNode(mixedContents(new TextSqlNode(" NAME = ?, ")), "true"
            )
        )));
    BoundSql boundSql = source.getBoundSql(null);
    assertEquals(expected, boundSql.getSql());
  }

  @Test
  void shouldTrimCommaAfterSET() throws Exception {
    final String expected = "UPDATE BLOG SET  NAME = ?";
    DynamicSqlSource source = createDynamicSqlSource(
      new TextSqlNode("UPDATE BLOG"),
      new SetSqlNode(new Configuration(), mixedContents(
        new IfSqlNode(mixedContents(new TextSqlNode("ID = ?")), "false"),
        new IfSqlNode(mixedContents(new TextSqlNode(", NAME = ?")), "true"))));
    BoundSql boundSql = source.getBoundSql(null);
    assertEquals(expected, boundSql.getSql());
  }

  @Test
  void shouldTrimNoSetClause() throws Exception {
    final String expected = "UPDATE BLOG";
    DynamicSqlSource source = createDynamicSqlSource(
        new TextSqlNode("UPDATE BLOG"),
        new SetSqlNode(new Configuration(),mixedContents(
            new IfSqlNode(mixedContents(new TextSqlNode("   , ID = ?   ")), "false"
            ),
            new IfSqlNode(mixedContents(new TextSqlNode(", NAME = ?  ")), "false"
            )
        )));
    BoundSql boundSql = source.getBoundSql(null);
    assertEquals(expected, boundSql.getSql());
  }

  @Test//ForEach Item In Collection
  void shouldIterateOnceForEachItemInCollection() throws Exception {
    final HashMap<String, String[]> parameterObject = new HashMap<String, String[]>() {{
      put("array", new String[]{"one", "two", "three"});
    }};
    final String expected = "SELECT * FROM BLOG WHERE ID in (  one = ? AND two = ? AND three = ? )";
    DynamicSqlSource source = createDynamicSqlSource(
        new TextSqlNode("SELECT * FROM BLOG WHERE ID in"),
        new ForEachSqlNode(new Configuration(),mixedContents(new TextSqlNode("${item} = #{item}")), "array", "index", "item", "(", ")", "AND"));
    BoundSql boundSql = source.getBoundSql(parameterObject);
    assertEquals(expected, boundSql.getSql());
    assertEquals(3, boundSql.getParameterMappings().size());
    assertEquals("__frch_item_0", boundSql.getParameterMappings().get(0).getProperty());
    assertEquals("__frch_item_1", boundSql.getParameterMappings().get(1).getProperty());
    assertEquals("__frch_item_2", boundSql.getParameterMappings().get(2).getProperty());
  }

  @Test//Ognl 表达式
  void shouldHandleOgnlExpression() throws Exception {
    // ForEach 集合参数
    final HashMap<String, String> parameterObject = new HashMap<String, String>() {{
      put("name", "Steve");
    }};
    final String expected = "Expression test: 3 / yes.";
    DynamicSqlSource source = createDynamicSqlSource(new TextSqlNode("Expression test: ${name.indexOf('v')} / ${name in {'Bob', 'Steve'\\} ? 'yes' : 'no'}."));
    // 设置ForEach 集合参数
    BoundSql boundSql = source.getBoundSql(parameterObject);
    assertEquals(expected, boundSql.getSql());
  }

  @Test//ForEach   Collection Is Empty
  void shouldSkipForEachWhenCollectionIsEmpty() throws Exception {
    // ForEach 集合参数
    final HashMap<String, Integer[]> parameterObject = new HashMap<String, Integer[]>() {{
        put("array", new Integer[] {});
    }};
    final String expected = "SELECT * FROM BLOG";
    DynamicSqlSource source = createDynamicSqlSource(new TextSqlNode("SELECT * FROM BLOG"),
        new ForEachSqlNode(new Configuration(), mixedContents(
            new TextSqlNode("#{item}")), "array", null, "item", "WHERE id in (", ")", ","));
    // 设置ForEach 集合参数
    BoundSql boundSql = source.getBoundSql(parameterObject);
    assertEquals(expected, boundSql.getSql());
    assertEquals(0, boundSql.getParameterMappings().size());
  }

  @Test
  void shouldPerformStrictMatchOnForEachVariableSubstitution() throws Exception {
    final Map<String, Object> param = new HashMap<>();
    final Map<String, String> uuu = new HashMap<>();
    uuu.put("u", "xyz");
    List<Bean> uuuu = new ArrayList<>();
    uuuu.add(new Bean("bean id"));
    param.put("uuu", uuu);
    param.put("uuuu", uuuu);
    DynamicSqlSource source = createDynamicSqlSource(
        new TextSqlNode("INSERT INTO BLOG (ID, NAME, NOTE, COMMENT) VALUES"),
        new ForEachSqlNode(new Configuration(),mixedContents(
            new TextSqlNode("#{uuu.u}, #{u.id}," +
              //list 的位置 即  list_0
              " #{ u,typeHandler=org.apache.ibatis.type.StringTypeHandler},"
              //list 的位置 即  list_0
                + " #{u:VARCHAR,typeHandler=org.apache.ibatis.type.StringTypeHandler}")),
          "uuuu", "uu", "u", "(", ")", ","));
    BoundSql boundSql = source.getBoundSql(param);
    System.out.println(boundSql.getSql());
    System.out.println(boundSql.getParameterMappings());
    assertEquals(4, boundSql.getParameterMappings().size());
    assertEquals("uuu.u", boundSql.getParameterMappings().get(0).getProperty());
    assertEquals("__frch_u_0.id", boundSql.getParameterMappings().get(1).getProperty());
    assertEquals("__frch_u_0", boundSql.getParameterMappings().get(2).getProperty());
    assertEquals("__frch_u_0", boundSql.getParameterMappings().get(3).getProperty());
  }

  private DynamicSqlSource createDynamicSqlSource(SqlNode... contents) throws IOException, SQLException {
    createBlogDataSource();
    final String resource = "org/apache/ibatis/builder/MapperConfig.xml";
    final Reader reader = Resources.getResourceAsReader(resource);
    SqlSessionFactory sqlMapper = new SqlSessionFactoryBuilder().build(reader);
    Configuration configuration = sqlMapper.getConfiguration();
    MixedSqlNode sqlNode = mixedContents(contents);
    return new DynamicSqlSource(configuration, sqlNode);
  }

  private MixedSqlNode mixedContents(SqlNode... contents) {
    return new MixedSqlNode(Arrays.asList(contents));
  }

  @Test  //参数为null
  void shouldMapNullStringsToEmptyStrings() {
    final String expected = "id=${id}";
    final MixedSqlNode sqlNode = mixedContents(new TextSqlNode(expected));
    final DynamicSqlSource source = new DynamicSqlSource(new Configuration(), sqlNode);
    String sql = source.getBoundSql(new Bean(null)).getSql();
    System.out.println(sql);
    Assertions.assertEquals("id=", sql);
  }

  public static class Bean {
    public String id;
    Bean(String property) {
      this.id = property;
    }
    public String getId() {
      return id;
    }
    public void setId(String property) {
      this.id = property;
    }
  }

}
