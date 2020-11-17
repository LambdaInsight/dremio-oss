/*
 * Copyright (C) 2017-2019 Dremio Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dremio.service.flight.impl;

import java.util.function.Supplier;

import javax.inject.Provider;

import org.apache.arrow.flight.CallStatus;

import com.dremio.common.utils.protos.QueryWritableBatch;
import com.dremio.exec.proto.GeneralRPCProtos;
import com.dremio.exec.proto.UserBitShared;
import com.dremio.exec.proto.UserProtos;
import com.dremio.exec.rpc.RpcOutcomeListener;
import com.dremio.exec.work.protector.UserResult;
import com.dremio.exec.work.protector.UserWorker;
import com.dremio.sabot.rpc.user.UserSession;
import com.dremio.service.flight.error.mapping.DremioFlightErrorMapper;
import com.dremio.service.flight.protector.CancellableUserResponseHandler;

/**
 * The UserResponseHandler that consumes a CreatePreparedStatementResponse.
 */
public class CreatePreparedStatementResponseHandler extends
  CancellableUserResponseHandler<UserProtos.CreatePreparedStatementArrowResp> {

  static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CreatePreparedStatementResponseHandler.class);

  public CreatePreparedStatementResponseHandler(UserBitShared.ExternalId prepareExternalId,
                                                UserSession userSession,
                                                Provider<UserWorker> workerProvider,
                                                Supplier<Boolean> isRequestCancelled) {
    super(prepareExternalId, userSession, workerProvider, isRequestCancelled);
  }

  @Override
  public void sendData(RpcOutcomeListener<GeneralRPCProtos.Ack> outcomeListener, QueryWritableBatch result) {
    throw new UnsupportedOperationException("A response sender based implementation should send no data to end users.");
  }

  @Override
  public void completed(UserResult result) {
    switch (result.getState()) {
      case COMPLETED:
        getCompletableFuture().complete(result.unwrap(UserProtos.CreatePreparedStatementArrowResp.class));
        break;
      case FAILED:
        getCompletableFuture().completeExceptionally(
          DremioFlightErrorMapper.toFlightRuntimeException(result.getException()));
        break;
      case CANCELED:
        final Exception canceledException = result.getException();
        getCompletableFuture().completeExceptionally(
          CallStatus.CANCELLED
            .withCause(canceledException)
            .withDescription(canceledException.getMessage())
            .toRuntimeException());
        break;

      case STARTING:
      case RUNNING:
      case NO_LONGER_USED_1:
      case ENQUEUED:
      default:
        getCompletableFuture().completeExceptionally(
          CallStatus.INTERNAL
            .withCause(new IllegalStateException())
            .withDescription("Internal Error: Invalid planning state.")
            .toRuntimeException());
        break;
    }
  }
}