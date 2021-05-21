package com.symphony.sfs.ms.chat.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.symphony.sfs.ms.starter.config.ExceptionHandling;
import com.symphony.sfs.ms.starter.testing.TestUtils;
import io.opentracing.Tracer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.zalando.problem.DefaultProblem;
import org.zalando.problem.Problem;
import org.zalando.problem.violations.ConstraintViolationProblem;
import org.zalando.problem.violations.Violation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.symphony.sfs.ms.starter.testing.MockMvcUtils.configuredGiven;
import static com.symphony.sfs.ms.starter.util.ProblemUtils.newConstraintViolation;
import static org.junit.jupiter.api.Assertions.fail;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HttpRequestUtils {

  public static <R, A> void postRequestFail(R request, A api, String endPoint, ObjectMapper objectMapper, Tracer tracer, String problemClassName, HttpStatus httpStatus) {
    postRequestFail(request, api, endPoint, Collections.emptyList(), objectMapper, tracer, problemClassName, httpStatus);
  }

  public static <R, A> void postRequestFail(R request, A api, String endPoint, List<Object> pathParams, ObjectMapper objectMapper, Tracer tracer, String problemClassName, HttpStatus httpStatus) {

    postRequestFail(request, api, endPoint, pathParams, objectMapper, tracer, problemClassName, httpStatus, null);
  }

  public static <R, A> void postRequestFail(R request, A api, String endPoint, List<Object> pathParams, ObjectMapper objectMapper, Tracer tracer, String problemClassName, HttpStatus httpStatus, String problemDetail) {

    Problem actualProblem = configuredGiven(objectMapper, new ExceptionHandling(tracer), api)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(request)
      .when()
      .post(endPoint, pathParams.toArray(new Object[pathParams.size()]))
      .then()
      .statusCode(httpStatus.value())
      .extract().response().body()
      .as(DefaultProblem.class);

    try {
      Problem expectedProblem = (Problem) Class.forName(problemClassName).getDeclaredConstructor(String.class).newInstance(problemDetail);
      TestUtils.testProblemEquality(expectedProblem, actualProblem);

    } catch (Exception e) {
      e.printStackTrace();
      fail();
    }
  }

  public static <R, A> void postRequestConstraintViolation(R request, A api, String endPoint, ObjectMapper objectMapper, Tracer tracer, String field, String... errorMessages) {

    ConstraintViolationProblem actualProblem = configuredGiven(objectMapper, new ExceptionHandling(tracer), api)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(request)
      .when()
      .post(endPoint)
      .then()
      .statusCode(HttpStatus.BAD_REQUEST.value())
      .extract().response().body()
      .as(ConstraintViolationProblem.class);

    List<Violation> violations = new ArrayList<>();
    for (String errorMessage : errorMessages) {
      violations.add(new Violation(field, errorMessage));
    }

    try {
      ConstraintViolationProblem expectedProblem = newConstraintViolation(violations);
      TestUtils.testConstraintViolationProblem(expectedProblem, actualProblem);
    } catch (Exception e) {
      e.printStackTrace();
      fail();
    }
  }

  public static <R, A> void getRequestFail(A api, R request, String endPoint, List<Object> pathParams, ObjectMapper objectMapper, Tracer tracer, String problemClassName, String problemErrorCode, HttpStatus httpStatus) {

    Problem actualProblem = configuredGiven(objectMapper, new ExceptionHandling(tracer), api)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(request)
      .when()
      .get(endPoint, pathParams.toArray(new Object[pathParams.size()]))
      .then()
      .statusCode(httpStatus.value())
      .extract().response().body()
      .as(DefaultProblem.class);

    try {
      Problem expectedProblem;
      if (problemErrorCode == null) {
        expectedProblem = (Problem) Class.forName(problemClassName).getDeclaredConstructor().newInstance();
      } else {
        expectedProblem = (Problem) Class.forName(problemClassName).getDeclaredConstructor(String.class).newInstance(problemErrorCode);
      }
      TestUtils.testProblemEquality(expectedProblem, actualProblem);

    } catch (
      Exception e) {
      e.printStackTrace();
      fail();
    }

  }
}
