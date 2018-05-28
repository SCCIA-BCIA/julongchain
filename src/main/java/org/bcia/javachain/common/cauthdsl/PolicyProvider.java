/**
 * Copyright Dingxuan. All Rights Reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bcia.javachain.common.cauthdsl;

import org.bcia.javachain.common.policies.IPolicy;
import org.bcia.javachain.msp.IIdentityDeserializer;
import org.bcia.javachain.msp.IMspManager;

/**
 * 类描述
 *
 * @author sunianle
 * @date 3/21/18
 * @company Dingxuan
 * @deprecated hangtianxinxi weihu
 */
public class PolicyProvider {

    public PolicyProvider(IIdentityDeserializer deserializer){}
//    public PolicyProvider(IMspManager manager){
//
//    }

    public IPolicy newPolicy(byte[] policyBytes) {
        return null;
    }
}
