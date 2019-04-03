/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.client.dataframe;

import org.elasticsearch.client.Validatable;
import org.elasticsearch.client.ValidationException;
import org.elasticsearch.client.dataframe.transforms.DataFrameTransformConfig;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

public class PutDataFrameTransformRequest implements ToXContentObject, Validatable {

    private final DataFrameTransformConfig config;

    public PutDataFrameTransformRequest(DataFrameTransformConfig config) {
        this.config = config;
    }

    public DataFrameTransformConfig getConfig() {
        return config;
    }

    @Override
    public Optional<ValidationException> validate() {
        ValidationException validationException = new ValidationException();
        if (config == null) {
            validationException.addValidationError("put requires a non-null data frame config");
            return Optional.of(validationException);
        } else {
            if (config.getId() == null) {
                validationException.addValidationError("data frame transform id cannot be null");
            }
            if (config.getSource() == null) {
                validationException.addValidationError("data frame transform source cannot be null");
            }
            if (config.getDestination() == null) {
                validationException.addValidationError("data frame transform destination cannot be null");
            }
        }

        if (validationException.validationErrors().isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(validationException);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        return config.toXContent(builder, params);
    }

    @Override
    public int hashCode() {
        return Objects.hash(config);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        PutDataFrameTransformRequest other = (PutDataFrameTransformRequest) obj;
        return Objects.equals(config, other.config);
    }
}
