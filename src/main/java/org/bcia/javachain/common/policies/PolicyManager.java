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
package org.bcia.javachain.common.policies;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.commons.lang3.StringUtils;
import org.bcia.javachain.common.exception.PolicyException;
import org.bcia.javachain.protos.common.Configtx;
import org.bcia.javachain.protos.common.Policies;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 对象
 *
 * @author zhouhui
 * @date 2018/4/17
 * @company Dingxuan
 */
public class PolicyManager implements IPolicyManager {
    private String path;
    private Map<String, IPolicy> policies;
    private Map<String, IPolicyManager> childManagers;

    public PolicyManager(String path, Map<Integer, IPolicyProvider> providers, Configtx.ConfigTree rootTree) throws
            InvalidProtocolBufferException, PolicyException {
        this.path = path;

        IPolicyProvider policyProvider = providers.get(Policies.Policy.PolicyType.IMPLICIT_META_VALUE);

        childManagers = new HashMap<String, IPolicyManager>();
        Iterator<Map.Entry<String, Configtx.ConfigTree>> childsIterator = rootTree.getChildsMap().entrySet().iterator();
        while (childsIterator.hasNext()) {
            Map.Entry<String, Configtx.ConfigTree> entry = childsIterator.next();
            String childName = entry.getKey();
            Configtx.ConfigTree configTree = entry.getValue();

            //递归构造子组织策略管理器
            IPolicyManager childPolicyManager = new PolicyManager(path + PolicyConstant.PATH_SEPARATOR + childName,
                    providers, configTree);
            childManagers.put(childName, childPolicyManager);
        }

        Iterator<Map.Entry<String, Configtx.ConfigPolicy>> policiesIterator = rootTree.getPoliciesMap().entrySet().iterator();
        while (policiesIterator.hasNext()) {
            Map.Entry<String, Configtx.ConfigPolicy> entry = policiesIterator.next();
            String policyName = entry.getKey();
            Configtx.ConfigPolicy configPolicy = entry.getValue();

            if (configPolicy.getPolicy().getType() == Policies.Policy.PolicyType.IMPLICIT_META_VALUE) {
                Policies.ImplicitMetaPolicy policy = Policies.ImplicitMetaPolicy.parseFrom(configPolicy.getPolicy()
                        .getValue());
                policies.put(policyName, new ImplicitMetaPolicy(policy, childManagers));
            } else {
                IPolicyProvider provider = providers.get(configPolicy.getPolicy().getType());
                IPolicy policy = provider.makePolicy(configPolicy.getPolicy().getValue().toByteArray());
                policies.put(policyName, policy);
            }
        }

        Iterator<Map.Entry<String, IPolicyManager>> managersIterator = childManagers.entrySet().iterator();
        while (managersIterator.hasNext()) {
            Map.Entry<String, IPolicyManager> entry = managersIterator.next();
            String childName = entry.getKey();
            IPolicyManager policyManager = entry.getValue();

            Iterator<Map.Entry<String, IPolicy>> policyIterator = policyManager.getPolicies().entrySet()
                    .iterator();
            while (policyIterator.hasNext()) {
                Map.Entry<String, IPolicy> policyEntry = policyIterator.next();
                String policyName = policyEntry.getKey();
                IPolicy policy = policyEntry.getValue();

                policies.put(childName + PolicyConstant.PATH_SEPARATOR + policyName, policy);
            }
        }
    }

    @Override
    public IPolicy getPolicy(String id) {
        if (StringUtils.isBlank(id)) {
            return new RejectPolicy();
        }

        String realPath = null;
        if (id.startsWith(PolicyConstant.PATH_SEPARATOR)) {
            if (!id.startsWith(PolicyConstant.PATH_SEPARATOR + path)) {
                return new RejectPolicy();
            }

            realPath = id.substring(1 + path.length());
        } else {
            realPath = id;
        }

        IPolicy policy = policies.get(realPath);
        if (policy != null) {
            return new NormalPolicy(policy, PolicyConstant.PATH_SEPARATOR + path + PolicyConstant.PATH_SEPARATOR + realPath);

        } else {
            return new RejectPolicy();
        }
    }

    @Override
    public IPolicyManager getSubPolicyManager(String[] path) {
        for (String str : path) {
            if (!childManagers.containsKey(str)) {
                return null;
            }
        }

        return childManagers.get(path[path.length - 1]);
    }

    @Override
    public Map<String, IPolicy> getPolicies() {
        return policies;
    }

    public Map<String, IPolicyManager> getChildManagers() {
        return childManagers;
    }
}

