Capybara.app_host = "http://localhost:8080"
Capybara.default_driver = :selenium

include SlingInterface
include SlingUsers

Before do
  @s = Sling.new()
  @um = UserManager.new(@s)
  @fm = SlingFile::FileManager.new(@s)
  @log = Logger.new(STDOUT)
  @m = Time.now.to_f.to_s.gsub(".", "")

  # users
  @bob = @um.create_user("bob" + @m)
  @carol = @um.create_user("carol" + @m)
  @ted = @um.create_user("ted" + @m)
  @alice = @um.create_user("alice" + @m)
  @users = {
      "bob" => @bob,
      "carol" => @carol,
      "ted" => @ted,
      "alice" => @alice
  }

  # set log level if an environment var is passed in. Values from Logger::Severity:
  # 0-5. 0 = debug, 5 = unknown.
  if ENV["NAKAMURA_TEST_LOG_LEVEL"] != nil
    @log.level = ENV["NAKAMURA_TEST_LOG_LEVEL"].to_i
  else
    @log.level = Logger::INFO
  end

end