@httpResponse
@fm
@filejson

Given /^I have logged out$/ do
  @s.switch_user(SlingUsers::User.anonymous)
end

When /^I create a new file$/ do
  @httpResponse = @fm.upload_pooled_file("file1", "file contents", "text/plain")
  @filejson = JSON.parse(@httpResponse.body)
  @log.info(@filejson)
end

Then /^I check the properties of the new file$/ do
  raise "Could not create the file!" unless @httpResponse.code.to_i == 201
  raise "File id was not returned" unless @filejson["file1"]["poolId"] != nil
  raise "File's mimetype is incorrect!" unless @filejson["file1"]["item"]["_mimeType"] == "text/plain"
  @log.info("File mimetype = #{@filejson["file1"]["item"]["_mimeType"]}")
  @log.info("File ID = #{@filejson["file1"]["poolId"]}")
end

Then /^I check the body content of the new file$/ do
  filebodyhttpresponse = @s.execute_get(@s.url_for("/p/#{@filejson["file1"]["poolId"]}"))
  raise "Could not fetch file content!" unless filebodyhttpresponse.code.to_i == 200
  raise "File content is incorrect" unless filebodyhttpresponse.body == "file contents"
end

Then /^I change the body content$/ do
  filemodificationpost = @fm.upload_pooled_file("file1", "modified contents", "text/plain", @filejson["file1"]["poolId"])
  raise "File modification post failed!" unless filemodificationpost.code.to_i == 200
  filebodyhttpresponse = @s.execute_get(@s.url_for("/p/#{@filejson["file1"]["poolId"]}"))
  raise "File content is incorrect" unless filebodyhttpresponse.body == "modified contents"
end

When /^I create a new file as anonymous$/ do
  @httpResponse = @fm.upload_pooled_file("file1", "file contents", "text/plain")
end

Then /^Anonymous user cannot post content$/ do
  raise "Anonymous user should not be able to post content" unless @httpResponse.code.to_i == 405
end