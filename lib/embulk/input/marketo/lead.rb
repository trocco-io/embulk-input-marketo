require "embulk/input/marketo/base"

module Embulk
  module Input
    module Marketo
      class Lead < Base
        include Timeslice

        PREVIEW_COUNT = 15

        Plugin.register_input("marketo/lead", self)

        def self.target
          :lead
        end

        def self.generate_columns(metadata)
          columns = [
            {name: "id", type: "long"},
            {name: "email", type: "string"},
          ]

          metadata.each do |field|
            type =
              case field[:data_type]
              when "integer"
                "long"
              when "datetime", "date"
                "timestamp"
              when "string", "text", "phone", "currency"
                "string"
              when "boolean"
                "boolean"
              when "float"
                "double"
              else
                "string"
              end

            columns << {name: field[:name], type: type}
          end

          columns
        end

        def run
          count = 0
          from_datetime = task[:from_datetime]
          to_datetime = task[:to_datetime]
          options = {}
          options[:batch_size] = PREVIEW_COUNT if preview?

          soap.each(from_datetime, to_datetime, options) do |lead|
            values = @columns.map do |column|
              name = column["name"].to_s
              value = (lead[name] || {})[:value]
              next unless value

              case column["type"]
              when "timestamp"
                Time.parse(value)
              else
                value
              end
            end

            page_builder.add(values)

            count += 1
            break if preview? && count >= PREVIEW_COUNT
          end

          page_builder.finish

          commit_report = {from_datetime: to_datetime}
          return commit_report
        end
      end
    end
  end
end