Capybara.app_host = "http://localhost:8080"
Capybara.default_driver = :selenium

include SlingInterface
include SlingUsers

Before do
  @s = Sling.new()
  @um = UserManager.new(@s)
  @fm = SlingFile::FileManager.new(@s)
  @log = Logger.new(STDOUT)
end