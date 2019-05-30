package org.apache.ibatis.a;

import org.apache.ibatis.BaseDataTest;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;

/**
 * Created by wangjs on 2019/5/30.
 */
public class Atest {

  public static void main(String[] args) throws IOException, SQLException {


    //创建derby数据库
    DataSource dataSource = BaseDataTest.createBlogDataSource();
    BaseDataTest.runScript(dataSource, BaseDataTest.BLOG_DDL);
    BaseDataTest.runScript(dataSource, BaseDataTest.BLOG_DATA);

    Connection connection = null;
    PreparedStatement preparedStatement = null;
    ResultSet resultSet = null;

    try {
      //1、加载数据库驱动
      Class.forName("org.apache.derby.jdbc.EmbeddedDriver");

      //2、通过驱动管理类获取数据库链接
      connection = DriverManager.getConnection("jdbc:derby:ibderby;create=true",
        "", "");

      //3、定义sql语句 ?表示占位符
      String sql = "SELECT * FROM blog where id = ?";

      //4、获取预处理statement
      preparedStatement = connection.prepareStatement(sql);

      //5、设置参数，第一个参数为sql语句中参数的序号（从1开始），第二个参数为设置的参数值
      preparedStatement.setInt(1, 1);

      //6、向数据库发出sql执行查询，查询出结果集   ,进入第3放jar执行
      resultSet = preparedStatement.executeQuery();

      //7、遍历查询结果集
      while (resultSet.next()) {
        System.out.println(resultSet.getString("id") + "  " + resultSet.getString("title"));
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      //8、释放资源
      if (resultSet != null) {
        try {
          resultSet.close();
        } catch (SQLException e) {

          e.printStackTrace();
        }
      }
      if (preparedStatement != null) {
        try {
          preparedStatement.close();
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }
      if (connection != null) {
        try {
          connection.close();
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }

    }

  }

}
