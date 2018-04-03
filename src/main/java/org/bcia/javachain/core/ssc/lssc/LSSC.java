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
package org.bcia.javachain.core.ssc.lssc;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.bcia.javachain.common.cauthdsl.CAuthDslBuilder;
import org.bcia.javachain.common.groupconfig.IApplicationConfig;
import org.bcia.javachain.common.log.JavaChainLog;
import org.bcia.javachain.common.log.JavaChainLogFactory;
import org.bcia.javachain.common.util.proto.ProtoUtils;
import org.bcia.javachain.core.aclmgmt.AclManagement;
import org.bcia.javachain.core.aclmgmt.resources.Resources;
import org.bcia.javachain.core.common.smartcontractprovider.ISmartContractPackage;
import org.bcia.javachain.core.common.smartcontractprovider.SmartContractCode;
import org.bcia.javachain.core.common.smartcontractprovider.SmartContractData;
import org.bcia.javachain.core.common.sysscprovider.ISystemSmartContractProvider;
import org.bcia.javachain.core.common.sysscprovider.SystemSmartContractFactory;
import org.bcia.javachain.core.node.NodeTool;
import org.bcia.javachain.core.policy.IPolicyChecker;
import org.bcia.javachain.core.policy.PolicyFactory;
import org.bcia.javachain.core.smartcontract.shim.impl.Response;
import org.bcia.javachain.core.smartcontract.shim.intfs.ISmartContractStub;
import org.bcia.javachain.core.ssc.SystemSmartContractBase;
import org.bcia.javachain.msp.mgmt.Principal;
import org.bcia.javachain.protos.common.Policies;
import org.bcia.javachain.protos.node.ProposalPackage;
import org.bcia.javachain.protos.node.ProposalResponsePackage;
import org.bcia.javachain.protos.node.Smartcontract;
import org.bcia.javachain.tools.configtxgen.entity.GenesisConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 用于智能合约生命周期管理的系统智能合约　Lifecycle System Smart Contract,LSSC
 *　The life cycle system smartcontract manages smartcontracts deployed
 *  on this peer. It manages smartcontracts via Invoke proposals.
 *    "Args":["deploy",<SmartcontractDeploymentSpec>]
 *    "Args":["upgrade",<SmartcontractDeploymentSpec>]
 *    "Args":["stop",<SmartcontractInvocationSpec>]
 *    "Args":["start",<SmartcontractInvocationSpec>]
 * @author sunianle
 * @date 3/5/18
 * @company Dingxuan
 */
@Component
public class LSSC  extends SystemSmartContractBase {
    private static JavaChainLog log = JavaChainLogFactory.getLog(LSSC.class);
    //INSTALL install command
    public final static String INSTALL="install";
    //DEPLOY deploy command
    public final static String DEPLOY="deploy";
    //UPGRADE upgrade smartcontract
    public final static String UPGRADE="upgrade";
    //GET_SC_INFO get smartcontract
    public final static String GET_SC_INFO="getid";
    //GETCCINFO get smartcontract
    public final static String GET_DEP_SPEC="getdepspec";
    //GET_SC_DATA get SmartcontractData
    public final static String GET_SC_DATA="getscdata";
    //GET_SMART_CONTRACTS gets the instantiated smartcontracts on a group
    public final static String GET_SMART_CONTRACTS="getsmartcontracts";
    //GETINSTALLEDMARTCONTRACTS gets the installed smartcontracts on a node
    public final static String GET_INSTALLED_SMARTCONTRACTS="getinstalledsmartcontracts";

    public final static String allowedCharsSmartContractName="[A-Za-z0-9_-]+";
    public final static String allowedCharsVersion="[A-Za-z0-9_.+-]+";

    @Autowired
    private LsscSupport support;

    private ISystemSmartContractProvider sscProvider;

    private IPolicyChecker checker;
    @Override
    public Response init(ISmartContractStub stub) {
        this.sscProvider=SystemSmartContractFactory.getSystemSmartContractProvider();
        this.checker= PolicyFactory.getPolicyChecker();
        log.info("Successfully initialized LSSC");
        return newSuccessResponse();
    }

    // Invoke implements lifecycle functions "deploy", "start", "stop", "upgrade".
    // Deploy's arguments -  {[]byte("deploy"), []byte(<chainname>), <unmarshalled pb.ChaincodeDeploymentSpec>}
    //
    // Invoke also implements some query-like functions
    // Get chaincode arguments -  {[]byte("getid"), []byte(<chainname>), []byte(<chaincodename>)}
    @Override
    public Response invoke(ISmartContractStub stub) {
        log.debug("Enter LSSC invoke function");
        List<byte[]> args = stub.getArgs();
        int size=args.size();
        if(size<1){
            return newErrorResponse(String.format("Incorrect number of arguments, %d",size));
        }
        String function= ByteString.copyFrom(args.get(0)).toStringUtf8();
        //Handle ACL:
        //1. get the signed proposal
        ProposalPackage.SignedProposal sp = stub.getSignedProposal();

        switch(function){
            case INSTALL:
                log.debug("Lifecycle Install");
                if(size<2){
                    return newErrorResponse(String.format("Incorrect number of arguments, %d",size));
                }
                // 2. check local MSP Admins policy
                if(checker.checkPolicyNoGroup(Principal.Admins,sp)==false){
                    return newErrorResponse(String.format("Authorization for INSTALL has been denied (error)"));
                }

                byte[] depSpec = args.get(1);
                try {
                    executeInstall(stub, depSpec);
                }catch (Exception e){
                    return newErrorResponse(String.format("Execute install failed, %s",e.getMessage()));
                }
                return newSuccessResponse("OK");
            case DEPLOY:
                //log.debug("Lifecycle Deploy");
            case UPGRADE:
                log.debug("Lifecycle Deploy/Upgrade:{}",function);
                if(size<3){
                    return newErrorResponse(String.format("Incorrect number of arguments, %d",size));
                }
                String groupName= ByteString.copyFrom(args.get(1)).toStringUtf8();
                if(isValidGroupName(groupName)==false){
                    return newErrorResponse(String.format("Invalid group name, %s",groupName));
                }
                IApplicationConfig ac=sscProvider.getApplicationConfig(groupName);
                if(ac==null){
                    return newErrorResponse(String.format("Programming error, non-existent appplication config for group '%s'",groupName));
                }
                //the maximum number of arguments depends on the capability of the channel
                if((ac.getCapabilities().privateGroupData()==false && size>6) ||
                (ac.getCapabilities().privateGroupData()==true && size>7)){
                    return newErrorResponse(String.format("Incorrect number of arguments, %d",size));
                }
                byte[] depSpec2=args.get(2);
                Smartcontract.SmartContractDeploymentSpec spec=null;
                try {
                    spec=ProtoUtils.getSmartContractDeploymentSpec(depSpec2);
                }catch (InvalidProtocolBufferException e){
                    return newErrorResponse(String.format("'%s'",e.getMessage()));
                }
                // optional arguments here (they can each be nil and may or may not be present)
                // args[3] is a marshalled SignaturePolicyEnvelope representing the endorsement policy
                // args[4] is the name of essc
                // args[5] is the name of vssc
                // args[6] is a marshalled CollectionConfigPackage struct
                byte[] ep=null;
                if(size>3 && args.get(3)!=null){
                    ep=args.get(3);
                }else{
                    Policies.SignaturePolicyEnvelope signaturePolicyEnvelope=CAuthDslBuilder.signedByAnyMember(NodeTool.getMspIDs(groupName));
                    ep=ProtoUtils.marshalOrPanic(signaturePolicyEnvelope);
                }

                byte[] essc=null;
                if(size>4 && args.get(4)!=null){
                    essc=args.get(4);
                }else{
                    essc=ByteString.copyFromUtf8("ESSC").toByteArray();
                }

                byte[] vssc=null;
                if(size>5 && args.get(5)!=null){
                    vssc=args.get(5);
                }else{
                    vssc=ByteString.copyFromUtf8("VSSC").toByteArray();
                }

                byte[] collectionsConfig=null;
                // we proceed with a non-nil collection configuration only if
                // we support the PrivateChannelData capability
                if(ac.getCapabilities().privateGroupData()==true  && size>6){
                    collectionsConfig=args.get(6);
                }

                SmartContractData cd=executeDeployOrUpgrade(stub, groupName, spec, ep, essc, vssc, collectionsConfig, function);
                if(cd==null){
                    return newErrorResponse(String.format("SmartContractData is null"));
                }
                byte[] cdbytes=cd.marshal();
                if(cdbytes==null){
                    return newErrorResponse(String.format("Marshal SmartContractData failed"));
                }
                return newSuccessResponse(cdbytes);
            case GET_SC_INFO:
                ;
            case GET_DEP_SPEC:
                ;
            case GET_SC_DATA:
                log.debug("Lifecycle GetScInfo/GetDepSpec/GetScData:{}",function);
                if(size!=3){
                    return newErrorResponse(String.format("Incorrect number of arguments, %d",size));
                }
                String groupName2=ByteString.copyFrom(args.get(1)).toStringUtf8();
                String smartContractName2=ByteString.copyFrom(args.get(2)).toStringUtf8();
                // 2. check local Group Readers policy
                String resource="";
                switch (function){
                    case GET_SC_INFO:
                        resource= Resources.LSSC_GETSCINFO;
                        break;
                    case GET_DEP_SPEC:
                        resource=Resources.LSSC_GETDEPSPEC;
                        break;
                    case GET_SC_DATA:
                        resource=Resources.LSSC_GETSCDATA;
                        break;
                }
                if(AclManagement.getACLProvider().checkACL(resource,groupName2,sp)==false){
                    return newErrorResponse(String.format("Authorization request failed %s: %s",groupName2,resource));
                }
                byte[] scbytes=getSmartContractInstance(stub, smartContractName2);
                if(scbytes==null){
                    log.error("Error getting smartcontract {} on group:{}",smartContractName2,groupName2);
                    return newErrorResponse(String.format("Error getting smartcontract %s on group:%s",groupName2,resource));
                }
                switch (function){
                    case GET_SC_INFO:
                        SmartContractData scd = getSmartContractData(smartContractName2, scbytes);
                        if(scd==null){
                            return newErrorResponse(String.format("SmartContractData is null"));
                        }
                        return newSuccessResponse(scd.smartContractName());
                    case GET_SC_DATA:
                        return newSuccessResponse(scbytes);
                    default:
                        byte[] depSpecByte=getSmartContractCode(smartContractName2, scbytes);
                        return newSuccessResponse(depSpecByte);
                }
            case GET_SMART_CONTRACTS:
                if(size!=1){
                    return newErrorResponse(String.format("Incorrect number of arguments, %d",size));
                }
                //2. check local MSP Admins policy
                if(checker.checkPolicyNoGroup(Principal.Admins,sp)==false){
                    return newErrorResponse(String.format("Authorization for INSTALL has been denied (error)"));
                }
                return getSmartContracts(stub);
            case GET_INSTALLED_SMARTCONTRACTS:
                if(size!=1){
                    return newErrorResponse(String.format("Incorrect number of arguments, %d",size));
                }
                //2. check local MSP Admins policy
                if(checker.checkPolicyNoGroup(Principal.Admins,sp)==false){
                    return newErrorResponse(String.format("Authorization for INSTALL has been denied (error)"));
                }
                return getInstalledSmartContracts();
            default:
                return newErrorResponse(String.format("Invilid Function %s",function));
        }
    }

    @Override
    public String getSmartContractStrDescription() {
        return "与生命周期管理相关的系统智能合约";
    }

    //create the smartcontract on the given chain
    private void putSmartContractData(ISmartContractStub stub,
                                      SmartContractData data){

    }

    // putSmartcontractCollectionData adds collection data for the smartcontract
    private void putSmartContractCollectionData(ISmartContractStub stub,
                                                SmartContractData data,
                                                byte[] collectionConfigBytes){

    }

    //checks for existence of smartcontract on the given channel
    private byte[] getSmartContractInstance(ISmartContractStub stub,
                                          String contractName){
        return null;
    }

    //gets the cd out of the bytes
    private SmartContractData getSmartContractData(String contractName,byte[] scdBytes){
        return null;
    }

    //checks for existence of smartcontract on the given chain
    private byte[] getSmartContractCode(String name, byte[] scdBytes){
        return null;
    }

    // getSmartcontracts returns all smartcontracts instantiated on this LSSC's group
    private Response getSmartContracts(ISmartContractStub stub){
        return null;
    }

    private Response getInstalledSmartContracts(){
        return null;
    }

    //check validity of chain name
    private boolean isValidGroupName(String group){
        return true;
    }

    // isValidSmartcontractName checks the validity of smartcontract name. Smartcontract names
    // should never be blank and should only consist of alphanumerics, '_', and '-'
    private boolean isValidSmartContractName(String contractName){
        return true;
    }

    // isValidSmartcontractVersion checks the validity of smartcontract version. Versions
    // should never be blank and should only consist of alphanumerics, '_',  '-',
    // '+', and '.'
    private boolean isValidSmartContractVersion(String contractName,String version){
        return true;
    }

    private boolean isValidSmartContractNameOrVersion(String scNameOrVersion,
                                                      String regExp){
        return true;
    }

    // executeInstall implements the "install" Invoke transaction
    private void executeInstall(ISmartContractStub stub,byte[] scBytes){
        log.debug("Execute install.");
    }

    // executeDeployOrUpgrade routes the code path either to executeDeploy or executeUpgrade
    // depending on its function argument
    private SmartContractData executeDeployOrUpgrade(ISmartContractStub stub,
                                                     String groupName,
                                                     Smartcontract.SmartContractDeploymentSpec scds,
                                                     byte [] policy,
                                                     byte [] escc,
                                                     byte [] vscc,
                                                     byte [] collectionConfigBytes,
                                                     String function){
         return null;
    }

    //executeDeploy implements the "instantiate" Invoke transaction
    private SmartContractData executeDeploy(
            ISmartContractStub stub,
            String groupName,
            Smartcontract.SmartContractDeploymentSpec scds,
            byte[] policy,
            byte[] escc,
            byte[] vscc,
            SmartContractData scdata,
            ISmartContractPackage scPackage,
            byte[] collectionConfigBytes
            ){
        return null;
    }


    //executeUpgrade implements the "upgrade" Invoke transaction.
    private SmartContractData executeUpgrade(
            ISmartContractStub stub,
            String groupName,
            Smartcontract.SmartContractDeploymentSpec scds,
            byte[] policy,
            byte[] escc,
            byte[] vscc,
            SmartContractData scdata,
            ISmartContractPackage scPackage
    ){
        return null;
    }


}

