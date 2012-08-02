
Then /^I tag the file with a single tag$/ do
  tagpath = "/tags/foo" + @m
  tagpost = @s.execute_post(@fileurl, ":operation" => "tag", "key" => tagpath)
  raise "Single tag could not be applied to file" unless tagpost.code.to_i == 200

  tagget = @s.execute_get(@s.url_for(tagpath))
  raise "Tag could not be retrieved" unless tagget.code.to_i == 200
  json = JSON.parse(tagget.body)
  raise "Tag resource type is incorrect" unless json["sling:resourceType"] == "sakai/tag"
  raise "Tag name is incorrect" unless json["sakai:tag-name"] == "foo" + @m
  raise "Tag count is incorrect" unless json["sakai:tag-count"] == 1

  fileget = @s.execute_get(@fileinfinityurl)
  json = JSON.parse(fileget.body)
  raise "Tag does not appear in file data" unless json["sakai:tags"][0] == "foo" + @m
end

Then /^I tag the file with multiple tags$/ do
  firsttag = "/tags/first" + @m
  secondtag = "/tags/second" + @m
  tagpost = @s.execute_post(@fileurl, ":operation" => "tag", "key" => [firsttag, secondtag])
  raise "Multiple tags could not be applied to file" unless tagpost.code.to_i == 200

  tagget = @s.execute_get(@s.url_for(firsttag))
  raise "First Tag could not be retrieved" unless tagget.code.to_i == 200
  json = JSON.parse(tagget.body)
  raise "First Tag resource type is incorrect" unless json["sling:resourceType"] == "sakai/tag"
  raise "First Tag name is incorrect" unless json["sakai:tag-name"] == "first" + @m
  raise "First Tag count is incorrect" unless json["sakai:tag-count"] == 1

  tagget = @s.execute_get(@s.url_for(secondtag))
  raise "Second Tag could not be retrieved" unless tagget.code.to_i == 200
  json = JSON.parse(tagget.body)
  raise "Second Tag resource type is incorrect" unless json["sling:resourceType"] == "sakai/tag"
  raise "Second Tag name is incorrect" unless json["sakai:tag-name"] == "second" + @m
  raise "Second Tag count is incorrect" unless json["sakai:tag-count"] == 1

  fileget = @s.execute_get(@fileinfinityurl)
  json = JSON.parse(fileget.body)
  raise "First Tag does not appear in file data" unless json["sakai:tags"].include?("first" + @m)
  raise "Second Tag does not appear in file data" unless json["sakai:tags"].include?("second" + @m)

end
