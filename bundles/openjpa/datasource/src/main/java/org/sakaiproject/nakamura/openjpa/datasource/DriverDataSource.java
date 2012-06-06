/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.sakaiproject.nakamura.openjpa.datasource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import javax.sql.DataSource;

public class DriverDataSource implements DataSource {
  private static final Logger LOG = LoggerFactory.getLogger(DriverDataSource.class);

  private String url;

  private Driver driver;

  private String username;

  private String password;

  private PrintWriter writer;

  public DriverDataSource(Class<Driver> driver, String url, String username, String password)
      throws SQLException {
    try {
      this.driver = driver.newInstance();
    } catch (IllegalAccessException e) {
      throw new SQLException(e.getMessage());
    } catch (InstantiationException e) {
      throw new SQLException(e.getMessage());
    }
    this.url = url;
    this.username = username;
    this.password = password;
    writer = DriverManager.getLogWriter();
  }

  public PrintWriter getLogWriter() throws SQLException {
    return writer;
  }

  public int getLoginTimeout() throws SQLException {
    return 0;
  }

  public void setLoginTimeout(int seconds) throws SQLException {
  }

  public void setLogWriter(PrintWriter writer) throws SQLException {
    this.writer = writer;
  }

  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    return iface.isAssignableFrom(DriverDataSource.class);
  }

  public Object unwrap(Class iface) throws SQLException {
    if (isWrapperFor(iface))
      return this;
    else
      return null;
  }

  public Connection getConnection() throws SQLException {
    return getConnection(null, null);
  }

  public Connection getConnection(String username, String password) throws SQLException {
    LOG.info("DriverDataSource.getConnection Classloader = " + this.getClass().getClassLoader());

    Properties props = new Properties();
    if (username == null)
      username = this.username;
    if (username != null)
      props.put("user", username);

    if (password == null)
      password = this.password;
    if (password != null)
      props.put("password", password);

    return getConnection(props);
  }

  public Connection getConnection(Properties props) throws SQLException {
    Connection conn = driver.connect(url, props);
    if (conn == null) {
      throw new SQLException("Can't connect to database: " + props);
    }
    return conn;
  }

}
