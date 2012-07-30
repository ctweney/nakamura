@allcomments
@commentid

When /^I grant everyone permission to read the file$/ do
  managepost = @fm.manage_members(@poolid, [ "everyone" ], [], [], [])
  @log.info(managepost)
end

When /^I grant everyone permission to manage the file$/ do
  @s.switch_user(SlingUsers::User.admin_user())
  managepost = @fm.manage_members(@poolid, ["everyone"], [], [ "everyone" ], [])
  @log.info(managepost.body)
end

Then /^A brand-new file has no comments on it$/ do
  commenturl = @s.url_for("/p/#{@poolid}.comments")
  commentget = @s.execute_get(commenturl)
  @allcomments = JSON.parse(commentget.body)
  raise "A new file should not have any comments" unless @allcomments[0] == nil
end

Then /^Make a bogus comment post$/ do
  posturl = @s.url_for("/p/#{@poolid}.comments")
  commentpost = @s.execute_post(posturl, { "foo" => "bar"})
  raise "Comment should have posted successfully" unless commentpost.code.to_i == 400
end

Then /^Comment on the file$/ do
  posturl = @s.url_for("/p/#{@poolid}.comments")
  commentpost = @s.execute_post(posturl, { "comment" => "something witty",
                                           "prop1" => "KERN-1536 allow arbitrary properties to be stored on a comment"})
  raise "Comment should have posted successfully" unless commentpost.code.to_i == 201
  comments = JSON.parse(commentpost.body)
  raise "Comment ID should have come back to us" unless comments["commentId"] != nil
end

Then /^Check that the comment was posted$/ do
  commenturl = @s.url_for("/p/#{@poolid}.comments")
  commentget = @s.execute_get(commenturl)
  @allcomments = JSON.parse(commentget.body)
  comment = @allcomments["comments"][0]
  @commentid = comment["commentId"]
  raise "The comment body is not present" unless comment["comment"] == "something witty"
  raise "The user hash was not recorded" unless comment["hash"] == @user.name
  raise "The comment author's profile is not present" unless comment["basic"]["elements"]["lastName"] != nil
  commentnodeget = @s.execute_get(@s.url_for("/p/#{@commentid}"))
  @log.info(commentnodeget)
  commentnodejson = JSON.parse(commentnodeget.body)
  raise "prop1 should have been stored" unless commentnodejson["prop1"] == "KERN-1536 allow arbitrary properties to be stored on a comment"
  contentget = @s.execute_get(@fileinfinityurl)
  contentjson = JSON.parse(contentget.body)
  raise "The comment count did not increment" unless contentjson["commentCount"] == 1
end

Then /^Edit an existing comment$/ do
  posturl = @s.url_for("/p/#{@poolid}.comments")
  commentpost = @s.execute_post(posturl, { "comment" => "modified witty", "commentId" => @commentid})
  raise "Edit the existing comment failed" unless commentpost.code.to_i == 200
  commenturl = @s.url_for("/p/#{@poolid}.comments")
  commentget = @s.execute_get(commenturl)
  json = JSON.parse(commentget.body)
  raise "The comment body is not present" unless json["comments"][0]["comment"] == "modified witty"
  contentget = @s.execute_get(@fileinfinityurl)
  contentjson = JSON.parse(contentget.body)
  raise "The comment count should still be 1" unless contentjson["commentCount"] == 1
end

Then /^Edit an existing comment as a non-managing viewer/ do
  posturl = @s.url_for("/p/#{@poolid}.comments")
  commentpost = @s.execute_post(posturl, { "comment" => "alice's witty rejoinder", "commentId" => @commentid})
  raise "Edit the existing comment as a non-managing user should not be possible" unless commentpost.code.to_i == 403
end

Then /^Edit an existing comment as a manager$/ do
  posturl = @s.url_for("/p/#{@poolid}.comments")
  commentpost = @s.execute_post(posturl, { "comment" => "alice's managerial rejoinder", "commentId" => @commentid})
  @log.info(commentpost)
  raise "Edit the existing comment as manager should be possible" unless commentpost.code.to_i == 200
end

Then /^Delete an existing comment$/ do
  @log.level = Logger::DEBUG
  deleteresponse = @s.delete_file(@s.url_for("/p/#{@poolid}.comments?commentId=#{@commentid}"))
  @log.info(deleteresponse)
  raise "Delete comment should have returned HTTP No Content" unless deleteresponse.code.to_i == 204
  contentget = @s.execute_get(@fileinfinityurl)
  contentjson = JSON.parse(contentget.body)
  raise "The comment count should now be 0" unless contentjson["commentCount"] == 0
end