package com.blueveery.springrest2ts.converters;

import com.blueveery.springrest2ts.implgens.EmptyImplementationGenerator;
import com.blueveery.springrest2ts.naming.ClassNameMapper;
import com.blueveery.springrest2ts.tsmodel.*;

/**
 * Created by tomek on 08.08.17.
 */
public class JavaEnumToTsUnionConverter extends ComplexTypeConverter {

    public JavaEnumToTsUnionConverter() {
        super(new EmptyImplementationGenerator());
    }

    public JavaEnumToTsUnionConverter(ClassNameMapper classNameMapper) {
        super(new EmptyImplementationGenerator(), classNameMapper);
    }

    @Override
    public boolean preConverted(ModuleConverter moduleConverter, Class javaClass) {
        if (TypeMapper.map(javaClass) == TypeMapper.tsAny) {
            TSModule tsModule = moduleConverter.getTsModule(javaClass);
            TSUnion tsUnion = new TSUnion();
            String aliasName = classNameMapper.mapJavaClassNameToTs(javaClass.getSimpleName());
            TSTypeAlias tsTypeAlias = new TSTypeAlias(aliasName, tsModule, tsUnion);
            tsModule.addScopedType(tsTypeAlias);
            TypeMapper.registerTsType(javaClass, tsTypeAlias);
            return true;
        }
        return false;
    }

    @Override
    public void convert(Class javaClass, NullableTypesStrategy nullableTypesStrategy) {
        TSTypeAlias tsTypeAlias = (TSTypeAlias) TypeMapper.map(javaClass);
        TSUnion tsUnion = (TSUnion) tsTypeAlias.getAliasedType();
        for (Object enumConstant : javaClass.getEnumConstants()) {
            String enumConstantStringValue = enumConstant.toString();
            TSLiteral tsLiteral = new TSLiteral(enumConstantStringValue, TypeMapper.tsString, enumConstantStringValue);
            tsUnion.getJoinedTsElementList().add(tsLiteral);
        }
        tsTypeAlias.addAllAnnotations(javaClass.getAnnotations());
        conversionListener.tsScopedTypeCreated(javaClass, tsTypeAlias);
    }
}
