# frozen_string_literal: true
# Generated by the protocol buffer compiler.  DO NOT EDIT!
# source: Message.proto

require 'google/protobuf'


descriptor_data = "\n\rMessage.proto\x12\rcommunication\"+\n\x07Message\x12\x0e\n\x06\x64\x65mand\x18\x01 \x01(\t\x12\x10\n\x08response\x18\x02 \x01(\tb\x06proto3"

pool = Google::Protobuf::DescriptorPool.generated_pool
pool.add_serialized_file(descriptor_data)

module Communication
  Message = ::Google::Protobuf::DescriptorPool.generated_pool.lookup("communication.Message").msgclass
end