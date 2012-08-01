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

end