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
package org.bcia.julongchain.common.exception;

/**
 * InvalidTx模块异常
 *
 * @author zhouhui
 * @date 2018/3/29
 * @company Dingxuan
 */
public class InvalidTxException extends JulongChainException {
    private static final String MODULE_NAME = "[InvalidTx]";

    public InvalidTxException() {
        super();
    }

    public InvalidTxException(String message) {
        super(MODULE_NAME + message);
    }

    public InvalidTxException(String message, Throwable cause) {
        super(MODULE_NAME + message, cause);
    }


    public InvalidTxException(Throwable cause) {
        super(cause);
    }


    protected InvalidTxException(String message, Throwable cause,
                                 boolean enableSuppression,
                                 boolean writableStackTrace) {
        super(MODULE_NAME + message, cause, enableSuppression, writableStackTrace);
    }
}
