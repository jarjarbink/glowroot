/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* global informant, $ */

informant.controller('JvmHeapDumpCtrl', [
  '$scope',
  '$http',
  'httpErrors',
  function ($scope, $http, httpErrors) {
    $('#directory').keypress(function (e) {
      if (e.which === 13) {
        // don't want to accidentally trigger heap dump
        return false;
      }
    });

    $scope.checkDiskSpace = function (deferred) {
      $scope.checkDiskSpaceResponse = false;
      $scope.heapDumpResponse = false;
      $http.post('backend/jvm/check-disk-space', { directory: $scope.data.directory })
          .success(function (data) {
            if (data.error) {
              deferred.reject(data.error);
            } else {
              $scope.checkDiskSpaceResponse = data;
              deferred.resolve('See disk space below');
            }
          })
          .error(function (data, status) {
            if (status === 0) {
              deferred.reject('Unable to connect to server');
            } else {
              deferred.reject('An error occurred');
            }
          });
    };

    $scope.dumpHeap = function (deferred) {
      $scope.checkDiskSpaceResponse = false;
      $scope.heapDumpResponse = false;
      $http.post('backend/jvm/dump-heap', { directory: $scope.data.directory })
          .success(function (data) {
            if (data.error) {
              deferred.reject(data.error);
            } else {
              deferred.resolve('Heap dump created');
              $scope.heapDumpResponse = data;
            }
          })
          .error(function (data, status) {
            if (status === 0) {
              deferred.reject('Unable to connect to server');
            } else {
              deferred.reject('An error occurred');
            }
          });
    };

    $http.get('backend/jvm/heap-dump-defaults')
        .success(function (data) {
          $scope.loaded = true;
          $scope.data = data;
        })
        .error(function (data, status) {
          $scope.loadingError = httpErrors.get(data, status);
        });
  }
]);
