package com.blueveery.springrest2ts.converters;

import com.blueveery.springrest2ts.implgens.ImplementationGenerator;
import com.blueveery.springrest2ts.tsmodel.*;
import com.fasterxml.jackson.annotation.*;

import java.beans.Introspector;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JacksonObjectMapper implements ObjectMapper {
    private final Map<Class, List<JsonIgnoreProperties>> jsonIgnorePropertiesPerClass = new HashMap<>();
    JsonAutoDetect.Visibility fieldsVisibility = JsonAutoDetect.Visibility.NONE;
    JsonAutoDetect.Visibility gettersVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY;
    JsonAutoDetect.Visibility isGetterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY;
    JsonAutoDetect.Visibility settersVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY;

    public JacksonObjectMapper() {
    }

    public JacksonObjectMapper(JsonAutoDetect.Visibility fieldsVisibility, JsonAutoDetect.Visibility gettersVisibility,
                               JsonAutoDetect.Visibility isGetterVisibility, JsonAutoDetect.Visibility settersVisibility) {
        this.fieldsVisibility = fieldsVisibility;
        this.gettersVisibility = gettersVisibility;
        this.isGetterVisibility = isGetterVisibility;
        this.settersVisibility = settersVisibility;
    }

    @Override
    public void addTypeLevelSpecificFields(Class javaType, TSComplexType tsComplexType) {
        JsonTypeInfo jsonTypeInfoAnnotation = (JsonTypeInfo) javaType.getAnnotation(JsonTypeInfo.class);
        if (jsonTypeInfoAnnotation != null) {
            switch (jsonTypeInfoAnnotation.include()) {
                case PROPERTY:
                    String propertyName = jsonTypeInfoAnnotation.property();
                    if ("".equals(propertyName)) {
                        propertyName = jsonTypeInfoAnnotation.use().toString();
                    }
                    for (TSField tsField : tsComplexType.getTsFields()) {
                        if (propertyName.equals(tsField.getName())) {
                            return;
                        }
                    }
                    TSField tsField = new TSField(propertyName, tsComplexType, TypeMapper.tsString);
                    tsComplexType.addTsField(tsField);
                    break;
                case WRAPPER_OBJECT:
                case WRAPPER_ARRAY:
                case EXTERNAL_PROPERTY:
                case EXISTING_PROPERTY:
                    break;
            }
        }
    }

    @Override
    public boolean filterClass(Class clazz) {
        return !containsIgnoreTypeAnnotation(clazz);
    }

    @Override
    public boolean filter(Field field) {
        if (commonFilter(field)) {
            return false;
        }
        JsonAutoDetect.Visibility currentFieldsVisibility = fieldsVisibility;
        JsonAutoDetect jsonAutoDetect = field.getDeclaringClass().getDeclaredAnnotation(JsonAutoDetect.class);
        if (jsonAutoDetect != null) {
            currentFieldsVisibility = setUpVisibility(jsonAutoDetect.fieldVisibility());
        }

        if (!currentFieldsVisibility.isVisible(field)) {
            return false;
        }
        if (containsIgnoreTypeAnnotation(field.getType())) {
            return false;
        }

        return true;
    }

    @Override
    public boolean filter(Method method, boolean isGetter) {
        if (commonFilter(method)) {
            return false;
        }
        JsonAutoDetect.Visibility currentGettersVisibility = gettersVisibility;
        JsonAutoDetect.Visibility currentSettersVisibility = settersVisibility;
        JsonAutoDetect.Visibility currentIsGetterVisibility = isGetterVisibility;
        JsonAutoDetect jsonAutoDetect = method.getDeclaringClass().getDeclaredAnnotation(JsonAutoDetect.class);
        if (jsonAutoDetect != null) {
            currentGettersVisibility = setUpVisibility(jsonAutoDetect.getterVisibility());
            currentIsGetterVisibility = setUpVisibility(jsonAutoDetect.isGetterVisibility());
            currentSettersVisibility = setUpVisibility(jsonAutoDetect.setterVisibility());

        }


        if (isGetter) {
            if (containsIgnoreTypeAnnotation(method.getReturnType())) {
                return false;
            }

            if (method.getName().startsWith("is") && !currentIsGetterVisibility.isVisible(method)) {
                return false;
            }
            if (!currentGettersVisibility.isVisible(method)) {
                return false;
            }

            if (!method.getName().startsWith("is") && !method.getName().startsWith("get")) {
                if(getJacksonPropertyNameBasedOnAnnnotation(method) == null){
                    return false;
                }
            }

        }else{
            if (!currentSettersVisibility.isVisible(method)) {
                return false;
            }

            if (!method.getName().startsWith("set")) {
                if(getJacksonPropertyNameBasedOnAnnnotation(method) == null){
                    return false;
                }
            }
        }

        return true;
    }

    private String getJacksonPropertyNameBasedOnAnnnotation(Method method) {
        JsonProperty jsonProperty = method.getAnnotation(JsonProperty.class);
        if (jsonProperty != null) {
            if ("".equals(jsonProperty.value())) {
                return method.getName();
            }else{
                return jsonProperty.value();
            }
        }

        JsonSetter jsonSetter = method.getAnnotation(JsonSetter.class);
        if (jsonSetter != null) {
            if ("".equals(jsonSetter.value())) {
                return method.getName();
            }else {
                return jsonSetter.value();
            }
        }

        JsonGetter jsonGetter = method.getAnnotation(JsonGetter.class);
        if (jsonGetter != null) {
            if ("".equals(jsonGetter.value())) {
                return method.getName();
            }else{
                return jsonGetter.value();
            }
        }
        return null;
    }

    @Override
    public List<TSField> mapJavaPropertyToField(Property property, TSComplexType tsComplexType,
                                                ComplexTypeConverter complexTypeConverter, ImplementationGenerator implementationGenerator) {
        List<TSField> tsFieldList = new ArrayList<>();
        Type fieldJavaGetterType = property.getGetterType();
        if (fieldJavaGetterType != null) {
            fieldJavaGetterType = applyJsonValue(fieldJavaGetterType);
            JsonUnwrapped declaredAnnotation = property.getDeclaredAnnotation(JsonUnwrapped.class);
            if (applyJsonUnwrapped(fieldJavaGetterType, declaredAnnotation, tsComplexType, tsFieldList, complexTypeConverter)){
                return tsFieldList;
            }
        }

        TSType fieldType;
        if(property.getGetterType() == property.getSetterType()) {
            fieldType = TypeMapper.map(fieldJavaGetterType);
        }else{
            TSType tsGetterType = TypeMapper.tsUndefined;
            if (fieldJavaGetterType != null) {
                tsGetterType = TypeMapper.map(fieldJavaGetterType);
            }

            if (property.getSetterType() != null) {
                TSType tsSetterType = TypeMapper.map(property.getSetterType());
                fieldType = new TSUnion(tsGetterType, tsSetterType);
            }else{
                fieldType = tsGetterType;
            }

        }


        TSField tsField = new TSField(property.getName(), tsComplexType, fieldType);
        if (!applyJsonIgnoreProperties(property, tsField)) {
            applyReadOnly(tsField, property);
            applyJsonFormat(tsField, property.getDeclaredAnnotation(JsonFormat.class));
            applyJacksonInject(tsField, property.getDeclaredAnnotation(JacksonInject.class));
            applyJsonRawValue(tsField, property.getDeclaredAnnotation(JsonRawValue.class));
            tsFieldList.add(tsField);
        }
        return tsFieldList;
    }

    @Override
    public String getPropertyName(Field field) {
        JsonProperty jsonProperty = field.getDeclaredAnnotation(JsonProperty.class);
        if (jsonProperty != null) {
            if ("".equals(jsonProperty.value())) {
                return field.getName();
            }else{
                jsonProperty.value();
            }
        }
        return field.getName();
    }

    @Override
    public String getPropertyName(Method method, boolean isGetter) {
        String name = getJacksonPropertyNameBasedOnAnnnotation(method);
        if (name == null) {
            if (isGetter && method.getName().startsWith("is")) {
                return cutPrefix(method.getName(), "is");
            }
            if (isGetter && method.getName().startsWith("get")) {
                return cutPrefix(method.getName(), "get");
            }

            if (!isGetter && method.getName().startsWith("set")) {
                return cutPrefix(method.getName(), "set");
            }
        }
        return name;
    }

    private String cutPrefix(String methodName, String prefix) {
        if (methodName.startsWith(prefix)) {
            return Introspector.decapitalize(methodName.replaceFirst(prefix, ""));
        }
        return null;
    }

    private Type applyJsonValue(Type fieldJavaType) {
        if (fieldJavaType instanceof Class) {
            Class fieldClass = (Class) fieldJavaType;
            for (Method method : fieldClass.getMethods()) {
                JsonValue jsonValue = method.getDeclaredAnnotation(JsonValue.class);
                if (jsonValue != null && jsonValue.value()) {
                    return method.getReturnType();
                }
            }
        }
        return fieldJavaType;
    }

    private boolean applyJsonUnwrapped(Type fieldJavaType, JsonUnwrapped declaredAnnotation, TSComplexType tsComplexType,
                                       List<TSField> tsFieldList, ComplexTypeConverter complexTypeConverter) {
        if (declaredAnnotation != null) {
            TSType tsType = TypeMapper.map(fieldJavaType);
            if (!(tsType instanceof TSComplexType)) {
                return false;
            }
            TSComplexType referredTsType = (TSComplexType) tsType;
            if (!referredTsType.isConverted()) {
                complexTypeConverter.convert((Class) fieldJavaType, conversionListener);
            }
            for (TSField nextTsField : referredTsType.getTsFields()) {
                tsFieldList.add(new TSField(nextTsField.getName(), tsComplexType, nextTsField.getType()));
            }
            return true;
        }
        return false;
    }

    private boolean applyJsonIgnoreProperties(Property property, TSField tsField) {
        List<JsonIgnoreProperties> jsonIgnorePropertiesList = discoverJsonIgnoreProperties(property.getDeclaringClass());
        for (JsonIgnoreProperties jsonIgnoreProperties : jsonIgnorePropertiesList) {
            for (String propertyName : jsonIgnoreProperties.value()) {
                if (propertyName.trim().equals(property.getName())) {
                    if (jsonIgnoreProperties.allowGetters() && property.getGetter() != null) {
                        if (!jsonIgnoreProperties.allowSetters() || property.getSetter() == null) {
                            tsField.setReadOnly(true);
                        }
                        return false;
                    }
                    if (jsonIgnoreProperties.allowSetters() && property.getSetter() != null) {
                        return false;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private void applyReadOnly(TSField tsField, Property property) {
        if(property.isReadOnly()){
            tsField.setReadOnly(true);
            return;
        }
        JsonProperty jsonProperty = property.getDeclaredAnnotation(JsonProperty.class);
        if (jsonProperty != null) {
            if (jsonProperty.access() == JsonProperty.Access.READ_ONLY) {
                tsField.setReadOnly(true);
            }
        }
    }

    private void applyJsonFormat(TSField tsField, JsonFormat jsonFormat) {
        if (jsonFormat != null) {
            switch (jsonFormat.shape()) {
                case ANY:
                    tsField.setType(TypeMapper.tsAny);
                    return;
                case SCALAR:
                    tsField.setType(TypeMapper.tsAny);
                    return;
                case ARRAY:
                    tsField.setType(new TSArray(TypeMapper.tsAny));
                    return;
                case OBJECT:
                    tsField.setType(TypeMapper.tsAny);
                    return;
                case NUMBER:
                    tsField.setType(TypeMapper.tsNumber);
                    return;
                case NUMBER_FLOAT:
                    tsField.setType(TypeMapper.tsNumber);
                    return;
                case NUMBER_INT:
                    tsField.setType(TypeMapper.tsNumber);
                    return;
                case STRING:
                    tsField.setType(TypeMapper.tsString);
                    return;
                case BOOLEAN:
                    tsField.setType(TypeMapper.tsBoolean);
                    return;
            }
        }
    }

    private void applyJacksonInject(TSField tsField, JacksonInject jacksonInject) {
        if (jacksonInject != null) {
            tsField.setReadOnly(true);
        }
    }

    private void applyJsonRawValue(TSField tsField, JsonRawValue jsonRawValue) {
        if (jsonRawValue != null && jsonRawValue.value()) {
            tsField.setType(TypeMapper.tsAny);
        }
    }

    private List<JsonIgnoreProperties> discoverJsonIgnoreProperties(Class<?> declaringClass) {
        if (!jsonIgnorePropertiesPerClass.containsKey(declaringClass)) {
            List<JsonIgnoreProperties> jsonIgnorePropertiesList = new ArrayList<>();
            JsonIgnoreProperties jsonIgnoreProperties = declaringClass.getAnnotation(JsonIgnoreProperties.class);
            if (jsonIgnoreProperties != null) {
                jsonIgnorePropertiesList.add(jsonIgnoreProperties);
            }
            for (Field field : declaringClass.getDeclaredFields()) {
                jsonIgnoreProperties = field.getAnnotation(JsonIgnoreProperties.class);
                if (jsonIgnoreProperties != null) {
                    jsonIgnorePropertiesList.add(jsonIgnoreProperties);
                }
            }
            for (Constructor<?> constructor : declaringClass.getDeclaredConstructors()) {
                jsonIgnoreProperties = constructor.getAnnotation(JsonIgnoreProperties.class);
                if (jsonIgnoreProperties != null) {
                    jsonIgnorePropertiesList.add(jsonIgnoreProperties);
                }
            }
            for (Method method : declaringClass.getDeclaredMethods()) {
                jsonIgnoreProperties = method.getAnnotation(JsonIgnoreProperties.class);
                if (jsonIgnoreProperties != null) {
                    jsonIgnorePropertiesList.add(jsonIgnoreProperties);
                }
            }

            jsonIgnorePropertiesPerClass.put(declaringClass, jsonIgnorePropertiesList);
        }
        return jsonIgnorePropertiesPerClass.get(declaringClass);
    }

    private boolean commonFilter(AccessibleObject member) {
        JsonBackReference jsonBackReference = member.getDeclaredAnnotation(JsonBackReference.class);
        if (jsonBackReference != null) {
            return true;
        }

        if (isJsonIgnoreActive(member)){
            return true;
        }
        return false;
    }

    private JsonAutoDetect.Visibility setUpVisibility(JsonAutoDetect.Visibility visibility) {
        return !isDefaultVisibility(visibility) ? visibility : JsonAutoDetect.Visibility.PUBLIC_ONLY;
    }

    private boolean containsIgnoreTypeAnnotation(Class<?> type) {
        JsonIgnoreType jsonIgnoreType = type.getDeclaredAnnotation(JsonIgnoreType.class);
        return jsonIgnoreType != null && jsonIgnoreType.value();
    }

    private boolean isJsonIgnoreActive(AccessibleObject member) {
        JsonIgnore jsonIgnore = member.getDeclaredAnnotation(JsonIgnore.class);
        if (jsonIgnore != null && jsonIgnore.value()) {
            return true;
        }
        return false;
    }

    private boolean isDefaultVisibility(JsonAutoDetect.Visibility visibility) {
        return visibility.equals(JsonAutoDetect.Visibility.DEFAULT);
    }
}
