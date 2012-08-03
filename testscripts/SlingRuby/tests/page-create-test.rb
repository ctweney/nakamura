#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura'
require 'nakamura/test'
include SlingInterface
include SlingUsers

class TC_NodeCreateTest < Test::Unit::TestCase
  include SlingTest

  def test_create_pool_item
    testpath = "/system/pool/createfile"
    res = @s.execute_post(@s.url_for(testpath), "a" => "foo", "b" => "bar")
	assert_equal("201",res.code)
	jsonResponse = JSON.parse(res.body)
	poolId = jsonResponse['_contentItem']['poolId']
	
    props = @s.get_node_props("/p/#{poolId}.json")
    assert_equal("foo", props["a"], "Expected property to be set")
    assert_equal("bar", props["b"], "Expected property to be set")
  end

  def test_update_pool_item
    testpath = "/system/pool/createfile"
    res = @s.execute_post(@s.url_for(testpath), "a" => "foo", "b" => "bar")
	assert_equal("201",res.code)
	jsonResponse = JSON.parse(res.body)
	poolId = jsonResponse['_contentItem']['poolId']
	
    props = @s.get_node_props("/p/#{poolId}.json")
    assert_equal("foo", props["a"], "Expected property to be set")
    assert_equal("bar", props["b"], "Expected property to be set")

    res = @s.execute_post(@s.url_for("/p/#{poolId}"), "a" => "foobar", "b" => "barfoo")
	assert_equal("200",res.code)
    props = @s.get_node_props("/p/#{poolId}.json")
    assert_equal("foobar", props["a"], "Expected property to be set")
    assert_equal("barfoo", props["b"], "Expected property to be set")

  end

end


